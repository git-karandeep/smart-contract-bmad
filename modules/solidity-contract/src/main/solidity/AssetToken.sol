// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessRegistry} from "./AccessRegistry.sol";
import {ERC721} from "@openzeppelin/contracts/token/ERC721/ERC721.sol";

contract AssetToken is ERC721 {
    error UnauthorizedTransfer();

    AccessRegistry public immutable registry;
    address public transferEscrow;

    event EscrowAddressUpdated(address indexed escrow);

    constructor(
        string memory name_,
        string memory symbol_,
        address registry_
    ) ERC721(name_, symbol_) {
        registry = AccessRegistry(registry_);
    }

    function setEscrow(address escrowAddress) external {
        if (!registry.hasRole(registry.ADMIN_ROLE(), msg.sender)) {
            revert UnauthorizedTransfer();
        }

        transferEscrow = escrowAddress;
        emit EscrowAddressUpdated(escrowAddress);
    }

    function mint(address to, uint256 tokenId) external {
        if (!registry.hasRole(registry.OPERATOR_ROLE(), msg.sender) && !registry.hasRole(registry.ADMIN_ROLE(), msg.sender)) {
            revert UnauthorizedTransfer();
        }

        _safeMint(to, tokenId);
    }

    function transferFrom(address from, address to, uint256 tokenId) public override {
        if (
            msg.sender != transferEscrow &&
            !registry.hasRole(registry.ADMIN_ROLE(), msg.sender) &&
            !registry.hasRole(registry.OPERATOR_ROLE(), msg.sender)
        ) {
            revert UnauthorizedTransfer();
        }

        super.transferFrom(from, to, tokenId);
    }
}
