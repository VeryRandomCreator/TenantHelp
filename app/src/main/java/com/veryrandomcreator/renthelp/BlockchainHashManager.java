package com.veryrandomcreator.renthelp;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

public class BlockchainHashManager {
    public static final String PIN_ERROR = "SECURITY PIN ALERT";
    public static final String NETWORK_ERROR = "NETWORK ERROR";
    public static final String JSON_ERROR = "JSON PARSE ERROR";
    public static final String HASH_MISMATCH = "HASH MISMATCH"; // check error message, if it is this, try again in a couple seconds

    private static final int NODE_QUORUM = 2;

    private static final ExecutorService networkExecutor = Executors.newCachedThreadPool();

    public static HashResult getLatestHash() {
        Future<NodeResponse> mempoolFuture = networkExecutor.submit(BlockchainHashManager::fetchMempool);
        Future<NodeResponse> blockstreamFuture = networkExecutor.submit(BlockchainHashManager::fetchBlockstream);
        Future<NodeResponse> blockchainFuture = networkExecutor.submit(BlockchainHashManager::fetchBlockchain);
        Future<NodeResponse> blockcypherFuture = networkExecutor.submit(BlockchainHashManager::fetchBlockcypher);

        List<String> responseStatus = new ArrayList<>();
        String finalConfirmedHash = null;
        try {
            List<NodeResponse> responses = new ArrayList<>();
            responses.add(mempoolFuture.get());
            responses.add(blockstreamFuture.get());
            responses.add(blockchainFuture.get());
            responses.add(blockcypherFuture.get());

            for (NodeResponse response : responses) {
                if (response.hash != null) {
                    responseStatus.add(response.nodeName + " responded with hash: " + response.hash);
                } else {
                    responseStatus.add(response.nodeName + " FAILED: " + response.errorMessage);
                }
            }

            for (int i = responses.size() - 1; i >= 0; i--) {
                if (responses.get(i).hash == null) {
                    if (responses.get(i).errorMessage != null && responses.get(i).errorMessage.contains(PIN_ERROR)) {
                        return new HashResult(new SecurityException("Potential MITM attack detected on " + responses.get(i).nodeName), responseStatus);
                    }
                    responses.remove(i);
                }
            }

            if (responses.size() < NODE_QUORUM) {
                return new HashResult(new IOException("Cannot establish timeline. Too many nodes are down."), responseStatus);
            }

            String confirmedHash = responses.get(0).hash;
            for (NodeResponse response : responses) {
                if (!response.hash.equals(confirmedHash)) {
                    return new HashResult(new SecurityException(HASH_MISMATCH), responseStatus);
                }
            }

            return new HashResult(confirmedHash, responseStatus);
        } catch (Exception e) {
            e.printStackTrace();
            return new HashResult(e, responseStatus);
        }
    }

    private static NodeResponse fetchMempool() {
        return fetchHashFromUrl("mempool.space", "https://mempool.space/api/blocks/tip/hash");
    }

    private static NodeResponse fetchBlockstream() {
        return fetchHashFromUrl("blockstream.info", "https://blockstream.info/api/blocks/tip/hash");
    }

    private static NodeResponse fetchBlockchain() {
        return fetchHashFromUrl("blockchain.info", "https://blockchain.info/q/latesthash");
    }

    private static NodeResponse fetchBlockcypher() {
        String urlString = "https://api.blockcypher.com/v1/btc/main";
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return new NodeResponse("api.blockcypher.com", "HTTP ERROR " + conn.getResponseCode(), new Exception("Bad Response"));
            }

            StringBuilder responseBuilder = new StringBuilder();

            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    responseBuilder.append(line);
                }
            } finally {
                conn.disconnect();
            }
            JSONObject jsonObject = new JSONObject(responseBuilder.toString());
            String parsedHash = jsonObject.getString("hash").trim();

            return new NodeResponse("api.blockcypher.com", parsedHash);
        } catch (SSLPeerUnverifiedException | SSLHandshakeException e) {
            return new NodeResponse("api.blockcypher.com", PIN_ERROR, e);
        } catch (IOException e) {
            return new NodeResponse("api.blockcypher.com", NETWORK_ERROR, e);
        } catch (Exception e) {
            return new NodeResponse("api.blockcypher.com", JSON_ERROR, e);
        }
    }

    private static NodeResponse fetchHashFromUrl(String nodeName, String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return new NodeResponse(nodeName, "HTTP ERROR " + conn.getResponseCode(), new Exception("Bad Response"));
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return new NodeResponse(nodeName, in.readLine().trim());
            } finally {
                conn.disconnect();
            }
        } catch (SSLPeerUnverifiedException | SSLHandshakeException e) {
            return new NodeResponse(nodeName, PIN_ERROR, e);
        } catch (IOException e) {
            return new NodeResponse(nodeName, NETWORK_ERROR, e);
        } catch (Exception e) {
            return new NodeResponse(nodeName, "Error", e);
        }
    }

    public static class HashResult {
        public final List<String> responseStatuses;
        public final String hash;
        public final Exception exception;

        public HashResult(Exception exception, List<String> responseStatuses) {
            this.exception = exception;
            this.responseStatuses = responseStatuses;
            this.hash = null;
        }

        public HashResult(String hash, List<String> responseStatuses) {
            this.exception = null;
            this.responseStatuses = responseStatuses;
            this.hash = hash;
        }
    }

    public static class NodeResponse {
        public final String nodeName;
        public final String hash;
        public final String errorMessage;

        public NodeResponse(String nodeName, String hash) {
            this.nodeName = nodeName;
            this.hash = hash;
            this.errorMessage = null;
        }

        public NodeResponse(String nodeName, String errorType, Exception e) {
            this.nodeName = nodeName;
            this.hash = null;
            this.errorMessage = errorType + ": " + e.getMessage();
        }
    }
}
