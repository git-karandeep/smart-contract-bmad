// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

// Requires OpenZeppelin Contracts v5.x:
//   npm install @openzeppelin/contracts
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {IERC721} from "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/**
 * @title TransferToken
 * @notice Transfers ERC-20 and ERC-721 tokens on behalf of the caller and emits
 *         dedicated events for each transfer, so they are easy to index off-chain.
 *
 * @dev The token contracts emit their own standard `Transfer` events. The events
 *      below are additional, emitted by this contract, and carry extra context
 *      (the operator, an optional memo/reference, and batch information).
 *
 *      Before calling:
 *        - ERC-20:  sender must call `token.approve(address(this), amount)`
 *        - ERC-721: sender must call `token.approve(address(this), tokenId)`
 *                   or `token.setApprovalForAll(address(this), true)`
 */
contract TransferToken is ReentrancyGuard {
    using SafeERC20 for IERC20;

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    event ERC20Transferred(
        address indexed token,
        address indexed from,
        address indexed to,
        uint256 amount,
        string memo
    );

    event ERC721Transferred(
        address indexed token,
        address indexed from,
        address indexed to,
        uint256 tokenId,
        string memo
    );

    event ERC20BatchTransferred(
        address indexed token,
        address indexed from,
        uint256 recipientCount,
        uint256 totalAmount
    );

    event ERC721BatchTransferred(
        address indexed token,
        address indexed from,
        address indexed to,
        uint256 count
    );

    // ---------------------------------------------------------------------
    // Errors
    // ---------------------------------------------------------------------

    error ZeroAddress();
    error ZeroAmount();
    error LengthMismatch();
    error EmptyBatch();

    // ---------------------------------------------------------------------
    // ERC-20
    // ---------------------------------------------------------------------

    /// @notice Transfer `amount` of an ERC-20 from the caller to `to`.
    /// @dev Emits the amount actually received, which can differ from `amount`
    ///      for fee-on-transfer tokens.
    function transferERC20(
        address token,
        address to,
        uint256 amount,
        string calldata memo
    ) external nonReentrant {
        uint256 received = _sendERC20(token, msg.sender, to, amount);
        emit ERC20Transferred(token, msg.sender, to, received, memo);
    }

    /// @notice Send an ERC-20 to many recipients in one transaction.
    function batchTransferERC20(
        address token,
        address[] calldata recipients,
        uint256[] calldata amounts,
        string calldata memo
    ) external nonReentrant {
        uint256 n = recipients.length;
        if (n == 0) revert EmptyBatch();
        if (n != amounts.length) revert LengthMismatch();

        uint256 total;
        for (uint256 i = 0; i < n; i++) {
            uint256 received = _sendERC20(token, msg.sender, recipients[i], amounts[i]);
            total += received;
            emit ERC20Transferred(token, msg.sender, recipients[i], received, memo);
        }

        emit ERC20BatchTransferred(token, msg.sender, n, total);
    }

    function _sendERC20(
        address token,
        address from,
        address to,
        uint256 amount
    ) private returns (uint256 received) {
        if (token == address(0) || to == address(0)) revert ZeroAddress();
        if (amount == 0) revert ZeroAmount();

        uint256 balanceBefore = IERC20(token).balanceOf(to);
        IERC20(token).safeTransferFrom(from, to, amount);
        received = IERC20(token).balanceOf(to) - balanceBefore;
    }

    // ---------------------------------------------------------------------
    // ERC-721
    // ---------------------------------------------------------------------

    /// @notice Transfer a single ERC-721 from the caller to `to`.
    function transferERC721(
        address token,
        address to,
        uint256 tokenId,
        string calldata memo
    ) external nonReentrant {
        if (token == address(0) || to == address(0)) revert ZeroAddress();

        IERC721(token).safeTransferFrom(msg.sender, to, tokenId);
        emit ERC721Transferred(token, msg.sender, to, tokenId, memo);
    }

    /// @notice Transfer several ERC-721 tokens from the same collection to one recipient.
    function batchTransferERC721(
        address token,
        address to,
        uint256[] calldata tokenIds,
        string calldata memo
    ) external nonReentrant {
        if (token == address(0) || to == address(0)) revert ZeroAddress();
        uint256 n = tokenIds.length;
        if (n == 0) revert EmptyBatch();

        for (uint256 i = 0; i < n; i++) {
            IERC721(token).safeTransferFrom(msg.sender, to, tokenIds[i]);
            emit ERC721Transferred(token, msg.sender, to, tokenIds[i], memo);
        }

        emit ERC721BatchTransferred(token, msg.sender, to, n);
    }
}
