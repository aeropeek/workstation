package org.janelia.workstation.core.filecache;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.janelia.it.jacs.shared.utils.StringUtils;
import org.janelia.workstation.core.api.http.HttpClientProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AbstractStorageClient} manager.
 */
public class StorageClientMgr {

    private static final Logger LOG = LoggerFactory.getLogger(StorageClientMgr.class);
    
    private static final Cache<String, AgentStorageClient> STORAGE_WORKERS_CACHE = CacheBuilder.newBuilder()
            .concurrencyLevel(4)
            .maximumSize(256)
            .build();
    private static final Consumer<Throwable> NOOP_ERROR_CONN_HANDLER = (t) -> {};

    private final HttpClientProxy httpClient;
    private final MasterStorageClient masterStorageClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a client with default authentication credentials.
     *
     * @param  baseUrl base URL
     * @param  httpClient http client
     *
     * @throws IllegalArgumentException
     *   if the baseUrl cannot be parsed.
     */
    public StorageClientMgr(String baseUrl, HttpClientProxy httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.masterStorageClient = new MasterStorageClient(baseUrl, httpClient, objectMapper);
    }

    private AgentStorageClient getStorageClientForStandardPath(String standardPathName) throws FileNotFoundException {
        String standardLocation = standardPathName.replaceFirst("^jade:\\/\\/", "").replace('\\', '/');
        Path lookupPath = Paths.get(standardLocation);
        int nPathComponents = lookupPath.getNameCount();
        List<String> storagePathPrefixCandidates = new LinkedList<>();
        IntStream.range(1, nPathComponents)
                .mapToObj(pathIndex -> {
                    if (lookupPath.getRoot() == null) {
                        return lookupPath.subpath(0, pathIndex);
                    } else {
                        return lookupPath.getRoot().resolve(lookupPath.subpath(0, pathIndex));
                    }
                })
                .map(p -> p.toString().replace('\\', '/'))
                .forEach(p -> storagePathPrefixCandidates.add(0, p));
        LOG.debug("storagePathPrefixCandidates={}", storagePathPrefixCandidates);
        AgentStorageClient storageClient;
        synchronized(STORAGE_WORKERS_CACHE) {
            for (String pathPrefix : storagePathPrefixCandidates) {
                storageClient = STORAGE_WORKERS_CACHE.getIfPresent(pathPrefix);
                if (storageClient != null) {
                    LOG.debug("Found storage client {} for {} in cache", storageClient.getBaseUrl(), pathPrefix);
                    return storageClient;
                }
            }
            LOG.info("Lookup storage client for {}", standardPathName);
            WebDavStorage storage = masterStorageClient.findStorage(standardPathName);
            String storageBindName = storage.getStorageBindName();
            String storageRootDir = storage.getStorageRootDir();
            LOG.info("Found WEBDAV storage for {}: {}, {}, {}",
                    standardPathName, storage.getRemoteFileUrl(), storageBindName, storageRootDir);

            String storageKey;
            Consumer<Throwable> agentErrorHandler;
            if  (storageBindName != null && standardLocation.startsWith(storageBindName)) {
                storageKey = storageBindName;
                agentErrorHandler = t -> {
                    LOG.info("Invalidate storage client for {} because of an error", storageKey, t);
                    STORAGE_WORKERS_CACHE.invalidate(storageKey);
                };
            } else if (storageRootDir != null && standardLocation.startsWith(storageRootDir)) {
                storageKey = storageRootDir;
                agentErrorHandler = t -> {
                    LOG.info("Invalidate storage client for {} because of an error", storageKey, t);
                    STORAGE_WORKERS_CACHE.invalidate(storageKey);
                };
            } else {
                storageKey = null;
                agentErrorHandler = NOOP_ERROR_CONN_HANDLER;
            }
            storageClient = new AgentStorageClient(
                    storage.getRemoteFileUrl(),
                    httpClient,
                    objectMapper,
                    agentErrorHandler
            );
            if (storageKey != null) {
                STORAGE_WORKERS_CACHE.put(storageKey, storageClient);
                LOG.info("Created storage client {} for {}", storageClient.getBaseUrl(), storageKey);
            } else {
                LOG.warn("No storage agent cached for {}", standardPathName);
            }
        }
        return storageClient;
    }
    
    /**
     * Finds information about the specified file.
     *
     * @param  remoteFileName  file's remote reference name.
     *
     * @return WebDAV information for the specified file.
     *
     * @throws WebDavException
     *   if the file information cannot be retrieved.
     */
    WebDavFile findFile(String remoteFileName) 
            throws WebDavException, FileNotFoundException {
        AgentStorageClient storageClient = getStorageClientForStandardPath(remoteFileName);
        return storageClient.findFile(remoteFileName);
    }

    String createStorage(String storageName, String storageContext, String storageTags) {
        return masterStorageClient.createStorage(storageName, storageContext, storageTags);
    }

    RemoteLocation uploadFile(File file, String storageURL, String storageLocation) {
        try {
            AgentStorageClient agentStorageClient = new AgentStorageClient(storageURL, httpClient, objectMapper, NOOP_ERROR_CONN_HANDLER);
            RemoteLocation remoteFile = agentStorageClient.saveFile(agentStorageClient.getUploadFileURL(storageLocation), file);
            remoteFile.setStorageURL(storageURL);
            return remoteFile;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    String urlEncodeComp(String pathComp) {
        if (StringUtils.isBlank(pathComp)) {
            return "";
        } else {
            try {
                return URLEncoder.encode(pathComp, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    String urlEncodeComps(String pathComps) {
        return StringUtils.isBlank(pathComps)
                ? ""
                : StreamSupport.stream(Splitter.on(File.separatorChar).split(pathComps).spliterator(), false)
                    .map(pc -> urlEncodeComp(pc))
                    .reduce(null, (c1, c2) -> c1 == null ? c2 : c1 + File.separatorChar + c2);
    }
}
