/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.fabric.service.jclouds;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import org.fusesource.fabric.api.ContainerProvider;
import org.fusesource.fabric.api.CreateContainerArguments;
import org.fusesource.fabric.api.CreateJCloudsContainerArguments;
import org.fusesource.fabric.api.FabricException;
import org.fusesource.fabric.api.JCloudsInstanceType;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.rest.RestContextFactory;


import static org.fusesource.fabric.internal.ContainerProviderUtils.DEFAULT_SSH_PORT;
import static org.fusesource.fabric.internal.ContainerProviderUtils.buildStartupScript;

/**
 * A concrete {@link org.fusesource.fabric.api.ContainerProvider} that creates {@link org.fusesource.fabric.api.Container}s via jclouds {@link ComputeService}.
 */
public class JcloudsContainerProvider implements ContainerProvider {

    private static final String IMAGE_ID = "imageId";
    private static final String LOCATION_ID = "locationId";
    private static final String HARDWARE_ID = "hardwareId";
    private static final String USER = "user";
    private static final String GROUP = "group";

    private static final String INSTANCE_TYPE = "instanceType";

    private final ConcurrentMap<String, ComputeService> computeServiceMap = new ConcurrentHashMap<String, ComputeService>();

    public void bind(ComputeService computeService) {
        if(computeService != null) {
            String providerName = computeService.getContext().getProviderSpecificContext().getId();
            if(providerName != null) {
              computeServiceMap.put(providerName,computeService);
            }
        }
    }

    public void unbind(ComputeService computeService) {
        if(computeService != null) {
            String providerName = computeService.getContext().getProviderSpecificContext().getId();
            if(providerName != null) {
               computeServiceMap.remove(providerName);
            }
        }
    }

    public ConcurrentMap<String, ComputeService> getComputeServiceMap() {
        return computeServiceMap;
    }

    /**
     * Creates an {@link org.fusesource.fabric.api.Container} with the given name pointing to the specified zooKeeperUrl.
     * @param proxyUri         The uri of the maven proxy to use.
     * @param containerUri     The uri that contains required information to build the Container.
     * @param name             The name of the Container.
     * @param zooKeeperUrl     The url of Zoo Keeper.
     * @param isEnsembleServer           Marks if the container will have the role of the ensemble server.
     * @param debugContainer
     */
    public void create(URI proxyUri, URI containerUri, String name, String zooKeeperUrl, boolean isEnsembleServer, boolean debugContainer) {
           create(proxyUri, containerUri,name,zooKeeperUrl,isEnsembleServer,debugContainer,1);
    }

    /**
     * Creates an {@link org.fusesource.fabric.api.Container} with the given name pointing to the specified zooKeeperUrl.
     * @param proxyUri          The uri of the maven proxy to use.
     * @param containerUri      The uri that contains required information to build the Container.
     * @param name              The name of the Container.
     * @param zooKeeperUrl      The url of Zoo Keeper.
     * @param isEnsembleServer   Marks if the container will have the role of the cluster server.
     * @param debugContainer        Flag used to enable debugging on the new Container.
     * @param number            The number of Container to create.
     */
    @Override
    public void create(URI proxyUri, URI containerUri, String name, String zooKeeperUrl, boolean isEnsembleServer, boolean debugContainer, int number) {
        String imageId = null;
        String hardwareId = null;
        String locationId = null;
        String group = null;
        String user = null;
        JCloudsInstanceType instanceType = JCloudsInstanceType.Smallest;
        String identity = null;
        String credential = null;
        String owner = null;

        try {
            String providerName = containerUri.getHost();

            if (containerUri.getQuery() != null) {
                Map<String, String> parameters = parseQuery(containerUri.getQuery());
                if (parameters != null) {
                    imageId = parameters.get(IMAGE_ID);
                    group = parameters.get(GROUP);
                    locationId = parameters.get(LOCATION_ID);
                    hardwareId = parameters.get(HARDWARE_ID);
                    user = parameters.get(USER);
                    if (parameters.get(INSTANCE_TYPE) != null) {
                        instanceType = JCloudsInstanceType.get(parameters.get(INSTANCE_TYPE), instanceType);
                    }
                }
            }

            doCreateContainer(proxyUri, name, number, zooKeeperUrl, isEnsembleServer, debugContainer, imageId, hardwareId, locationId, group, user, instanceType, providerName, identity, credential, owner, DEFAULT_SSH_PORT);
        } catch (FabricException e) {
            throw e;
        } catch (Exception e) {
            throw new FabricException(e);
        }
    }

    /**
     * Creates an {@link org.fusesource.fabric.api.Container} with the given name pointing to the specified zooKeeperUrl.
     * @param proxyUri         The uri of the maven proxy to use.
     * @param containerUri     The uri that contains required information to build the Container.
     * @param name             The name of the Container.
     * @param zooKeeperUrl     The url of Zoo Keeper.
     */
    public void create(URI proxyUri, URI containerUri, String name, String zooKeeperUrl) {
        create(proxyUri,containerUri, name, zooKeeperUrl);
    }

    @Override
    public boolean create(CreateContainerArguments createArgs, String name, String zooKeeperUrl) throws Exception {
        if (createArgs instanceof CreateJCloudsContainerArguments) {
            CreateJCloudsContainerArguments args = (CreateJCloudsContainerArguments) createArgs;
            return doCreateContainer(args, name, zooKeeperUrl, DEFAULT_SSH_PORT) != null;
        }
        return false;
    }

    protected String doCreateContainer(CreateJCloudsContainerArguments args, String name, String zooKeeperUrl, int returnPort) throws MalformedURLException, RunNodesException, URISyntaxException {
        boolean isClusterServer = args.isEnsembleServer();
        boolean debugContainer = args.isDebugContainer();
        int number = args.getNumber();
        String imageId = args.getImageId();
        String hardwareId = args.getHardwareId();
        String locationId = args.getLocationId();
        String group = args.getGroup();
        String user = args.getUser();
        JCloudsInstanceType instanceType = args.getInstanceType();
        String providerName = args.getProviderName();
        String identity = args.getIdentity();
        String credential = args.getCredential();
        String owner = args.getOwner();
        URI proxyURI = args.getProxyUri();

        return doCreateContainer(proxyURI, name, number, zooKeeperUrl, isClusterServer, debugContainer, imageId, hardwareId, locationId, group, user, instanceType, providerName, identity, credential, owner, returnPort);
    }

    /**
     * Creates a new fabric on a remote JClouds machine, returning the new ZK connection URL
     */
    public String createClusterServer(CreateJCloudsContainerArguments createArgs, String name) throws Exception {
        // TODO how can we get this value from the tarball I wonder, in case it ever changes?
        int zkPort = 2181;
        createArgs.setEnsembleServer(true);
        return doCreateContainer(createArgs, name, null, zkPort);
    }

    protected String doCreateContainer(URI proxyUri, String name, int number, String zooKeeperUrl, boolean isEnsembleServer, boolean debugContainer, String imageId, String hardwareId, String locationId, String group, String user, JCloudsInstanceType instanceType, String providerName, String identity, String credential, String owner, int returnPort) throws MalformedURLException, RunNodesException, URISyntaxException {
        ComputeService computeService = computeServiceMap.get(providerName);
        if (computeService == null) {
            //Iterable<? extends Module> modules = ImmutableSet.of(new Log4JLoggingModule(), new JschSshClientModule());
            Iterable<? extends Module> modules = ImmutableSet.of();

            Properties props = new Properties();
            props.put("provider", providerName);
            props.put("identity", identity);
            props.put("credential", credential);
            if (!Strings.isNullOrEmpty(owner)) {
                props.put("jclouds.ec2.ami-owners", owner);
            }

            RestContextFactory restFactory = new RestContextFactory();
            ComputeServiceContext context = new ComputeServiceContextFactory(restFactory).createContext(providerName, identity, credential, modules, props);
            computeService = context.getComputeService();
        }

        TemplateBuilder builder = computeService.templateBuilder();
        builder.any();
        switch (instanceType) {
            case Smallest:
                builder.smallest();
                break;
            case Biggest:
                builder.biggest();
                break;
            case Fastest:
                builder.fastest();
        }

        if (locationId != null) {
            builder.locationId(locationId);
        }
        if (imageId != null) {
            builder.imageId(imageId);
        }
        if (hardwareId != null) {
            builder.hardwareId(hardwareId);
        }

        Set<? extends NodeMetadata> metadatas = null;
        Credentials credentials = null;
        if (user != null && credentials == null) {
            credentials = new Credentials(user, null);
        }

        metadatas = computeService.createNodesInGroup(group, number, builder.build());

        int suffix = 1;
        StringBuilder buffer = new StringBuilder();
        boolean first = true;
        if (metadatas != null) {
            for (NodeMetadata nodeMetadata : metadatas) {
                String id = nodeMetadata.getId();
                Set<String> publicAddresses = nodeMetadata.getPublicAddresses();
                for (String pa: publicAddresses) {
                    if (first) {
                        first = false;
                    } else {
                        buffer.append(",");
                    }
                    buffer.append(pa + ":" + returnPort);
                }
                String containerName = name;
                if(number > 1) {
                    containerName+=suffix++;
                }
                String script = buildStartupScript(proxyUri, containerName, "~/", zooKeeperUrl, DEFAULT_SSH_PORT,isEnsembleServer, debugContainer);
                if (credentials != null) {
                    computeService.runScriptOnNode(id, script, RunScriptOptions.Builder.overrideCredentialsWith(credentials).runAsRoot(false));
                } else {
                    computeService.runScriptOnNode(id, script);
                }
            }
        }
        return buffer.toString();
    }

    public Map<String, String> parseQuery(String uri) throws URISyntaxException {
        //TODO: This is copied form URISupport. We should move URISupport to core so that we don't have to copy stuff arround.
        try {
            Map<String, String> rc = new HashMap<String, String>();
            if (uri != null) {
                String[] parameters = uri.split("&");
                for (int i = 0; i < parameters.length; i++) {
                    int p = parameters[i].indexOf("=");
                    if (p >= 0) {
                        String name = URLDecoder.decode(parameters[i].substring(0, p), "UTF-8");
                        String value = URLDecoder.decode(parameters[i].substring(p + 1), "UTF-8");
                        rc.put(name, value);
                    } else {
                        rc.put(parameters[i], null);
                    }
                }
            }
            return rc;
        } catch (UnsupportedEncodingException e) {
            throw (URISyntaxException) new URISyntaxException(e.toString(), "Invalid encoding").initCause(e);
        }
    }
}