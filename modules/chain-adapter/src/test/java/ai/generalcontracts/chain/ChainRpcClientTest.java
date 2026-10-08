package ai.generalcontracts.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.http.HttpService;

class ChainRpcClientTest {
    private HttpServer server;
    private Web3j web3j;
    private boolean emptyLatestBlock;

    @BeforeEach
    void startRpcServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String result = request.contains("eth_chainId")
                ? "0x7a69"
                : emptyLatestBlock ? "null" : "0x10";
            byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"" + result + "\"}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        web3j = new JsonRpc2_0Web3j(new HttpService("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stopRpcServer() {
        if (web3j != null) {
            web3j.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @Tag("integration")
    void readsChainIdAndLatestBlockFromRpc() {
        ChainStatus status = new ChainRpcClient(web3j).status();

        assertEquals(BigInteger.valueOf(31337), status.chainId());
        assertEquals(BigInteger.valueOf(16), status.latestBlock());
    }

    @Test
    @Tag("integration")
    void rejectsAnUnexpectedConfiguredChainId() {
        ChainConnectionException exception = assertThrows(
            ChainConnectionException.class,
            () -> new ChainRpcClient(web3j, BigInteger.ONE).status()
        );

        assertEquals("The configured EVM RPC endpoint returned an unexpected chain ID", exception.getMessage());
    }

    @Test
    @Tag("integration")
    void rejectsAnIncompleteLatestBlockResponse() {
        emptyLatestBlock = true;

        ChainConnectionException exception = assertThrows(
            ChainConnectionException.class,
            () -> new ChainRpcClient(web3j).status()
        );

        assertEquals(
            true,
            exception.getMessage().startsWith("Unable to query the configured EVM RPC endpoint")
        );
    }

    @Test
    @Tag("integration")
    void reportsUnreachableEndpointWithoutLeakingItsUrl() throws IOException {
        int port = server.getAddress().getPort();
        server.stop(0);
        web3j.shutdown();
        Web3j unavailable = new JsonRpc2_0Web3j(new HttpService("http://user:secret@127.0.0.1:" + port));

        ChainConnectionException exception = assertThrows(
            ChainConnectionException.class,
            () -> new ChainRpcClient(unavailable).status()
        );

        assertEquals(true, exception.getMessage().startsWith("Unable to query the configured EVM RPC endpoint"));
        assertEquals(false, exception.getMessage().contains("127.0.0.1"));
        assertEquals(false, exception.getMessage().contains("secret"));
        unavailable.shutdown();
    }
}
