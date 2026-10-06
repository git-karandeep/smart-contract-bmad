// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

contract AccessRegistry is AccessControl {
    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant COMPLIANCE_ROLE = keccak256("COMPLIANCE_ROLE");
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");

    constructor(address admin) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(ADMIN_ROLE, admin);
    }

    function grantAdmin(address account) external onlyRole(ADMIN_ROLE) {
        _grantRole(DEFAULT_ADMIN_ROLE, account);
        _grantRole(ADMIN_ROLE, account);
    }

    function revokeAdmin(address account) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _revokeRole(DEFAULT_ADMIN_ROLE, account);
        _revokeRole(ADMIN_ROLE, account);
    }

    function grantOperator(address account) external onlyRole(ADMIN_ROLE) {
        _grantRole(OPERATOR_ROLE, account);
    }

    function revokeOperator(address account) external onlyRole(ADMIN_ROLE) {
        _revokeRole(OPERATOR_ROLE, account);
    }

    function grantCompliance(address account) external onlyRole(ADMIN_ROLE) {
        _grantRole(COMPLIANCE_ROLE, account);
    }

    function revokeCompliance(address account) external onlyRole(ADMIN_ROLE) {
        _revokeRole(COMPLIANCE_ROLE, account);
    }
}
