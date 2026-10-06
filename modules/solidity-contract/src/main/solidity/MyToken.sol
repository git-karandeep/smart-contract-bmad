// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

// Requires OpenZeppelin Contracts v5.x:
//   npm install @openzeppelin/contracts
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {ERC20Burnable} from "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import {ERC20Capped} from "@openzeppelin/contracts/token/ERC20/extensions/ERC20Capped.sol";
import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title MyToken
 * @notice ERC-20 token with owner-only minting, a hard supply cap, and burning.
 */
contract MyToken is ERC20, ERC20Burnable, ERC20Capped, Ownable {
    /**
     * @param name_          Token name, e.g. "My Token"
     * @param symbol_        Token symbol, e.g. "MTK"
     * @param cap_           Maximum total supply (in smallest units, 18 decimals)
     * @param initialSupply  Amount minted to the owner at deployment
     * @param owner_         Address that can mint and owns the contract
     */
    constructor(
        string memory name_,
        string memory symbol_,
        uint256 cap_,
        uint256 initialSupply,
        address owner_
    ) ERC20(name_, symbol_) ERC20Capped(cap_) Ownable(owner_) {
        if (initialSupply > 0) {
            _mint(owner_, initialSupply);
        }
    }

    /// @notice Mint new tokens. Only the owner can call this; reverts if cap is exceeded.
    function mint(address to, uint256 amount) external onlyOwner {
        _mint(to, amount);
    }

    // Required override: ERC20 and ERC20Capped both hook into _update
    function _update(address from, address to, uint256 value)
        internal
        override(ERC20, ERC20Capped)
    {
        super._update(from, to, value);
    }
}

/**
 * @title TokenFactory
 * @notice Deploys new MyToken contracts on demand. The caller becomes the owner of the new token.
 */
contract TokenFactory {
    address[] public allTokens;
    mapping(address => address[]) public tokensByCreator;

    event TokenCreated(
        address indexed creator,
        address indexed token,
        string name,
        string symbol,
        uint256 cap,
        uint256 initialSupply
    );

    function createToken(
        string calldata name_,
        string calldata symbol_,
        uint256 cap_,
        uint256 initialSupply
    ) external returns (address) {
        MyToken token = new MyToken(name_, symbol_, cap_, initialSupply, msg.sender);
        address tokenAddr = address(token);

        allTokens.push(tokenAddr);
        tokensByCreator[msg.sender].push(tokenAddr);

        emit TokenCreated(msg.sender, tokenAddr, name_, symbol_, cap_, initialSupply);
        return tokenAddr;
    }

    function totalTokens() external view returns (uint256) {
        return allTokens.length;
    }
}
