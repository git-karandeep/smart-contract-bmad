# Story 2.1 — Transfer API walkthrough

Target: `06d1bec66de4d56b05d522513ed2f580743027d6` (`feat(transfers): secure instruction API routes`)

## [ ] Intent — in progress · current · changed during review: no

**Problem:** Story 2.1 requires an authenticated, durable transfer-instruction API, but its route and error contract are only resolved by the broader Epic 2 plan and the existing API route may remain denied by the current security policy.

**Approach:** Reuse the existing transfer API, Mongo instruction model, and exact-decimal handling; close only verified Story 2.1 gaps and cover the request, persistence, and status-query behavior with focused tests.

*Source: the [Story 2.1 plan](../plan-2-1-create-transfer-instruction-api-and-persistence-model.md).*

## [ ] Broad strokes — unvisited · changed during review: no

- [TransferController](../../../modules/contracts/src/main/java/ai/generalcontracts/startup/controller/TransferController.java) is the create/status boundary and delegates durable work to the lifecycle service.
- [SecurityConfiguration](../../../modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java) admits only the designated transfer HTTP methods and authenticated JWT principals.
- [ClientCertificateFilter](../../../modules/contracts/src/main/java/ai/generalcontracts/startup/security/ClientCertificateFilter.java) keeps transfer routes inside the existing client-certificate-protected scope.
- [TransferRouteSecurityTest](../../../modules/contracts/src/test/java/ai/generalcontracts/startup/TransferRouteSecurityTest.java) exercises both gates together and checks denied callers do not reach lifecycle operations.

## [ ] Security slice — route admission — unvisited · changed during review: no

The route authorization adds JWT-principal checks for `POST /api/transfers` and `GET /api/transfers/{instructionId}` while the default-deny policy remains in force. Separately, the transfer paths are added to the client-certificate filter's protected scope: both the authenticated JWT and the pre-existing client-certificate requirement still apply; neither replaces the other.

The method-specific JWT checks are in [SecurityConfiguration](../../../modules/contracts/src/main/java/ai/generalcontracts/startup/config/SecurityConfiguration.java), and the certificate requirement is applied by [ClientCertificateFilter](../../../modules/contracts/src/main/java/ai/generalcontracts/startup/security/ClientCertificateFilter.java). [Route-security tests](../../../modules/contracts/src/test/java/ai/generalcontracts/startup/TransferRouteSecurityTest.java) cover unauthenticated/non-JWT callers, JWT callers without a certificate, and requests outside the two intended route shapes.

## [ ] Security slice — wallet and instruction ownership — unvisited · changed during review: no

Route-level admission does not grant authority over an arbitrary wallet. The controller still checks the authenticated principal's tenant and requires source-wallet `WRITE` entitlement before creating an instruction; it records that principal and tenant with the command. Status reads check the stored initiating principal and tenant, then require `READ` entitlement for the instruction's source wallet before returning the response.

These per-wallet and owner checks remain in [TransferController](../../../modules/contracts/src/main/java/ai/generalcontracts/startup/controller/TransferController.java), not in the route matcher. The [controller tests](../../../modules/contracts/src/test/java/ai/generalcontracts/startup/controller/TransferControllerTest.java) cover owner-only status access and the read entitlement; the [route-security tests](../../../modules/contracts/src/test/java/ai/generalcontracts/startup/TransferRouteSecurityTest.java) verify a missing source-wallet entitlement prevents persistence.

## [ ] Periphery — unvisited · changed during review: no

- The added [route-security tests](../../../modules/contracts/src/test/java/ai/generalcontracts/startup/TransferRouteSecurityTest.java) also check invalid amount rejection, safe status projection, and that adjacent/unlisted paths remain denied.
- The [controller tests](../../../modules/contracts/src/test/java/ai/generalcontracts/startup/controller/TransferControllerTest.java) assert the initiating principal cannot read another principal's instruction.
- The controller remains deployment-gated by `app.transfer.api.enabled=true`; see [TransferController](../../../modules/contracts/src/main/java/ai/generalcontracts/startup/controller/TransferController.java).
