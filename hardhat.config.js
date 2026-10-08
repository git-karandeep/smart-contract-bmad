import { defineConfig } from "hardhat/config";
import hardhatEthers from "@nomicfoundation/hardhat-ethers";
import hardhatEthersChaiMatchers from "@nomicfoundation/hardhat-ethers-chai-matchers";
import hardhatMocha from "@nomicfoundation/hardhat-mocha";

export default defineConfig({
  plugins: [hardhatEthers, hardhatEthersChaiMatchers, hardhatMocha],
  defaultNetwork: "hardhatMainnet",
  solidity: {
    version: "0.8.30",
    settings: {
      optimizer: { enabled: true, runs: 200 }
    }
  },
  networks: {
    hardhatMainnet: {
      type: "edr-simulated",
      chainType: "l1",
      chainId: 31337
    },
    localhost: {
      type: "http",
      chainType: "l1",
      url: process.env.ANVIL_RPC_URL || `http://127.0.0.1:${process.env.ANVIL_HOST_PORT || "8545"}`,
      chainId: 31337
    }
  },
  paths: {
    sources: "./modules/solidity-contract/src/main/solidity",
    tests: "./modules/solidity-contract/src/test",
    cache: "./modules/solidity-contract/cache",
    artifacts: "./modules/solidity-contract/artifacts"
  }
});
