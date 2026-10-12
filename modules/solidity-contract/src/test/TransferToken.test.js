import { expect } from "chai";
import hre from "hardhat";

const { ethers } = await hre.network.create();

describe("TransferToken", function () {
  async function deployFixture() {
    const [owner, recipient, other] = await ethers.getSigners();
    const tokenFactory = await ethers.getContractFactory("MyToken");
    const token = await tokenFactory.deploy(
      "Foundation Token",
      "FND",
      ethers.parseEther("1000"),
      ethers.parseEther("100"),
      owner.address
    );
    const routerFactory = await ethers.getContractFactory("TransferToken");
    const router = await routerFactory.deploy();
    return { token, router, owner, recipient, other };
  }

  it("moves only approved ERC-20 units and emits the observed transfer amount", async function () {
    const { token, router, owner, recipient } = await deployFixture();
    const amount = ethers.parseEther("12.5");
    await token.approve(await router.getAddress(), amount);

    await expect(router.transferERC20(await token.getAddress(), recipient.address, amount, "transfer-1"))
      .to.emit(router, "ERC20Transferred")
      .withArgs(await token.getAddress(), owner.address, recipient.address, amount, "transfer-1");
    expect(await token.balanceOf(recipient.address)).to.equal(amount);
  });

  it("rejects unapproved movement and zero-value or zero-address transfers", async function () {
    const { token, router, owner, recipient, other } = await deployFixture();
    const amount = ethers.parseEther("1");

    await expect(router.connect(other).transferERC20(await token.getAddress(), recipient.address, amount, ""))
      .to.revert(ethers);
    await expect(router.transferERC20(await token.getAddress(), recipient.address, 0, ""))
      .to.be.revertedWithCustomError(router, "ZeroAmount");
    await expect(router.transferERC20(await token.getAddress(), ethers.ZeroAddress, amount, ""))
      .to.be.revertedWithCustomError(router, "ZeroAddress");
    expect(await token.balanceOf(owner.address)).to.equal(ethers.parseEther("100"));
    expect(await token.balanceOf(recipient.address)).to.equal(0n);
  });
});
