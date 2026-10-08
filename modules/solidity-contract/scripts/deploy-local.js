import hre from "hardhat";

async function main() {
  const rpcUrl = new URL(
    process.env.ANVIL_RPC_URL || `http://127.0.0.1:${process.env.ANVIL_HOST_PORT || "8545"}`
  );
  if (!["localhost", "127.0.0.1", "[::1]", "::1"].includes(rpcUrl.hostname)) {
    throw new Error("Local contract deployment only supports loopback RPC endpoints");
  }

  const { ethers } = await hre.network.connect("localhost");
  const chainId = BigInt(await ethers.provider.send("eth_chainId", []));
  if (chainId !== 31337n) {
    throw new Error("Local contract deployment requires chain ID 31337");
  }

  const [deployer] = await ethers.getSigners();
  const token = await ethers.deployContract("MyToken", [
    "Foundation Token",
    "FND",
    ethers.parseEther("1000000"),
    ethers.parseEther("1000"),
    deployer.address
  ]);
  await token.waitForDeployment();
  console.log(`MyToken deployed at ${await token.getAddress()}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
