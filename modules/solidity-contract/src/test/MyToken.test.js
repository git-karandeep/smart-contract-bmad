import { expect } from "chai";
import hre from "hardhat";

const { ethers } = await hre.network.create();

describe("MyToken", function () {
  async function deployToken() {
    const [owner, other] = await ethers.getSigners();
    const tokenFactory = await ethers.getContractFactory("MyToken");
    const token = await tokenFactory.deploy(
      "Foundation Token",
      "FND",
      ethers.parseEther("1000"),
      ethers.parseEther("100"),
      owner.address
    );
    return { token, owner, other };
  }

  it("mints initial supply and restricts minting to its owner", async function () {
    const { token, owner, other } = await deployToken();

    expect(await token.totalSupply()).to.equal(ethers.parseEther("100"));
    await expect(token.connect(other).mint(other.address, 1n)).to.revert(ethers);
    await token.mint(other.address, 1n);
    expect(await token.balanceOf(other.address)).to.equal(1n);
    expect(await token.owner()).to.equal(owner.address);
  });

  it("enforces the configured supply cap", async function () {
    const { token, other } = await deployToken();

    await expect(token.mint(other.address, ethers.parseEther("1000"))).to.revert(ethers);
  });

  it("allows normal transfers and reverts on insufficient balance or allowance without changing balances", async function () {
    const { token, owner, other } = await deployToken();

    await token.transfer(other.address, 10n);
    expect(await token.balanceOf(other.address)).to.equal(10n);

    await expect(token.connect(other).transfer(owner.address, 11n))
      .to.be.revertedWithCustomError(token, "ERC20InsufficientBalance");
    await expect(token.connect(other).transferFrom(owner.address, other.address, 1n))
      .to.be.revertedWithCustomError(token, "ERC20InsufficientAllowance");
    expect(await token.balanceOf(other.address)).to.equal(10n);
  });

  it("restricts pause and unpause to the owner", async function () {
    const { token, other } = await deployToken();

    await expect(token.connect(other).pause())
      .to.be.revertedWithCustomError(token, "OwnableUnauthorizedAccount");
    await token.pause();
    await expect(token.connect(other).unpause())
      .to.be.revertedWithCustomError(token, "OwnableUnauthorizedAccount");
  });

  it("blocks every transfer path while paused, including the owner, and resumes after unpause", async function () {
    const { token, owner, other } = await deployToken();
    await token.transfer(other.address, 10n);
    await token.connect(other).approve(owner.address, 5n);
    await token.pause();

    await expect(token.transfer(other.address, 1n)).to.be.revertedWithCustomError(token, "EnforcedPause");
    await expect(token.connect(other).transfer(owner.address, 1n)).to.be.revertedWithCustomError(token, "EnforcedPause");
    await expect(token.transferFrom(other.address, owner.address, 1n)).to.be.revertedWithCustomError(token, "EnforcedPause");
    await expect(token.mint(other.address, 1n)).to.be.revertedWithCustomError(token, "EnforcedPause");
    await expect(token.connect(other).burn(1n)).to.be.revertedWithCustomError(token, "EnforcedPause");
    expect(await token.balanceOf(other.address)).to.equal(10n);

    await token.unpause();
    await token.connect(other).transfer(owner.address, 1n);
    expect(await token.balanceOf(other.address)).to.equal(9n);
  });
});