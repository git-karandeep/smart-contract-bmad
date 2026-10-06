                                                                            // SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {IERC721} from "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import {AccessRegistry} from "./AccessRegistry.sol";

contract SettlementEscrow is AccessControl {
    using SafeERC20 for IERC20;

    error AlreadyProcessed(bytes32 businessId);
    error SettlementNotFound(bytes32 businessId);
    error SettlementNotFunded(bytes32 businessId);
    error SettlementAlreadySettled(bytes32 businessId);
    error SettlementAlreadyCancelled(bytes32 businessId);
    error UnauthorizedSettlement(address caller);

    AccessRegistry public immutable registry;

    struct Settlement {
        address assetToken;
        uint256 assetId;
        address payer;
        address recipient;
        address paymentToken;
        uint256 amount;
        bool funded;
        bool settled;
        bool cancelled;
    }

    mapping(bytes32 => Settlement) private settlements;
    mapping(bytes32 => bool) private processed;

    event EscrowFunded(
        bytes32 indexed businessId,
        address indexed assetToken,
        uint256 indexed assetId,
        address payer,
        address recipient,
        address paymentToken,
        uint256 amount
    );

    event SettlementExecuted(
        bytes32 indexed businessId,
        address indexed recipient,
        uint256 assetId,
        uint256 amount
    );

    event SettlementCancelled(
        bytes32 indexed businessId,
        address indexed payer,
        uint256 assetId,
        uint256 amount
    );

    constructor(address registryAddress) {
        registry = AccessRegistry(registryAddress);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
    }

    modifier onlyOperator() {
        if (!registry.hasRole(registry.OPERATOR_ROLE(), msg.sender)) {
            revert UnauthorizedSettlement(msg.sender);
        }
        _;
    }

    function fundEscrow(
        bytes32 businessId,
        address assetToken,
        uint256 assetId,
        address payer,
        address recipient,
        address paymentToken,
        uint256 amount
    ) external onlyOperator {
        if (processed[businessId]) {
            revert AlreadyProcessed(businessId);
        }

        if (payer == address(0) || recipient == address(0)) {
            revert UnauthorizedSettlement(msg.sender);
        }

        Settlement storage settlement = settlements[businessId];
        settlement.assetToken = assetToken;
        settlement.assetId = assetId;
        settlement.payer = payer;
        settlement.recipient = recipient;
        settlement.paymentToken = paymentToken;
        settlement.amount = amount;
        settlement.funded = true;

        processed[businessId] = true;

        if (assetToken != address(0) && assetId != 0) {
            IERC721(assetToken).safeTransferFrom(payer, address(this), assetId);
        }

        if (paymentToken != address(0) && amount > 0) {
            IERC20(paymentToken).safeTransferFrom(payer, address(this), amount);
        }

        emit EscrowFunded(businessId, assetToken, assetId, payer, recipient, paymentToken, amount);
    }

    function executeSettlement(bytes32 businessId) external onlyOperator {
        Settlement storage settlement = settlements[businessId];

        if (settlement.assetToken == address(0) && settlement.paymentToken == address(0) && settlement.amount == 0 && !settlement.funded) {
            revert SettlementNotFound(businessId);
        }

        if (!settlement.funded) {
            revert SettlementNotFunded(businessId);
        }

        if (settlement.settled) {
            revert SettlementAlreadySettled(businessId);
        }

        if (settlement.cancelled) {
            revert SettlementAlreadyCancelled(businessId);
        }

        if (settlement.assetToken != address(0) && settlement.assetId != 0) {
            IERC721(settlement.assetToken).safeTransferFrom(address(this), settlement.recipient, settlement.assetId);
        }

        if (settlement.paymentToken != address(0) && settlement.amount > 0) {
            IERC20(settlement.paymentToken).safeTransfer(settlement.recipient, settlement.amount);
        }

        settlement.settled = true;

        emit SettlementExecuted(businessId, settlement.recipient, settlement.assetId, settlement.amount);
    }

    function cancelSettlement(bytes32 businessId) external onlyOperator {
        Settlement storage settlement = settlements[businessId];

        if (settlement.assetToken == address(0) && settlement.paymentToken == address(0) && settlement.amount == 0 && !settlement.funded) {
            revert SettlementNotFound(businessId);
        }

        if (!settlement.funded) {
            revert SettlementNotFunded(businessId);
        }

        if (settlement.settled) {
            revert SettlementAlreadySettled(businessId);
        }

        if (settlement.cancelled) {
            revert SettlementAlreadyCancelled(businessId);
        }

        if (settlement.assetToken != address(0) && settlement.assetId != 0) {
            IERC721(settlement.assetToken).safeTransferFrom(address(this), settlement.payer, settlement.assetId);
        }

        if (settlement.paymentToken != address(0) && settlement.amount > 0) {
            IERC20(settlement.paymentToken).safeTransfer(settlement.payer, settlement.amount);
        }

        settlement.cancelled = true;

        emit SettlementCancelled(businessId, settlement.payer, settlement.assetId, settlement.amount);
    }

    function getSettlement(bytes32 businessId)
        external
        view
        returns (
            address assetToken,
            uint256 assetId,
            address payer,
            address recipient,
            address paymentToken,
            uint256 amount,
            bool funded,
            bool settled,
            bool cancelled
        )
    {
        Settlement storage settlement = settlements[businessId];
        return (
            settlement.assetToken,
            settlement.assetId,
            settlement.payer,
            settlement.recipient,
            settlement.paymentToken,
            settlement.amount,
            settlement.funded,
            settlement.settled,
            settlement.cancelled
        );
    }
}
