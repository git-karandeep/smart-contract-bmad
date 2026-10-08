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
});
