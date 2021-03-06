/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.loadtest;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import com.sitewhere.core.Boilerplate;
import com.sitewhere.loadtest.server.ConfigurationResolver;
import com.sitewhere.loadtest.spi.agent.IAgentManager;
import com.sitewhere.loadtest.spi.agent.ILoadTestAgent;
import com.sitewhere.loadtest.spi.server.IConfigurationResolver;
import com.sitewhere.loadtest.spi.server.ILoadTestServer;
import com.sitewhere.loadtest.spi.server.IServerManager;
import com.sitewhere.loadtest.spring.ILoadTestBeans;
import com.sitewhere.loadtest.version.IVersion;
import com.sitewhere.loadtest.version.VersionHelper;
import com.sitewhere.server.lifecycle.LifecycleComponent;
import com.sitewhere.spi.ServerStartupException;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.server.lifecycle.ILifecycleProgressMonitor;
import com.sitewhere.spi.server.lifecycle.LifecycleComponentType;

/**
 * Primary server component of load tester node.
 * 
 * @author Derek
 */
public class LoadTestServer extends LifecycleComponent implements ILoadTestServer {

    /** Static logger instance */
    private static Logger LOGGER = LogManager.getLogger();

    /** Spring context for server */
    public static ApplicationContext SERVER_SPRING_CONTEXT;

    /** Contains version information */
    private IVersion version = VersionHelper.getVersion();

    /** Configuratino resolver */
    private IConfigurationResolver configurationResolver = new ConfigurationResolver();

    /** Server manager implementation */
    private IServerManager serverManager;

    /** Agent manager implementation */
    private IAgentManager agentManager;

    /** Server startup error */
    private ServerStartupException serverStartupError;

    public LoadTestServer() {
	super(LifecycleComponentType.Other);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.server.lifecycle.LifecycleComponent#initialize(com.
     * sitewhere.spi.server.lifecycle.ILifecycleProgressMonitor)
     */
    @Override
    public void initialize(ILifecycleProgressMonitor monitor) throws SiteWhereException {
	// Load Spring beans configuration.
	initializeSpringContext();

	// Initialize server manager implementation.
	initializeServerManager();

	// Initialize agent manager.
	initializeAgentManager();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.sitewhere.server.lifecycle.LifecycleComponent#start(com.sitewhere.spi
     * .server.lifecycle.ILifecycleProgressMonitor)
     */
    @Override
    public void start(ILifecycleProgressMonitor monitor) throws SiteWhereException {
	// Start server manager implementation.
	startNestedComponent(getServerManager(), monitor, true);

	// Start agent manager implementation.
	startNestedComponent(getAgentManager(), monitor, true);

	// Show banner indicating that server has started.
	showServerBanner();

	LOGGER.info("Starting load tests for all agents...");
	for (ILoadTestAgent<?> agent : getAgentManager().getAgents()) {
	    agent.startLoadTests();
	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.sitewhere.server.lifecycle.LifecycleComponent#stop(com.sitewhere.spi.
     * server.lifecycle.ILifecycleProgressMonitor)
     */
    @Override
    public void stop(ILifecycleProgressMonitor monitor) throws SiteWhereException {
	if (getAgentManager() != null) {
	    getAgentManager().lifecycleStop(monitor);
	}
	if (getServerManager() != null) {
	    getServerManager().lifecycleStop(monitor);
	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.server.lifecycle.LifecycleComponent#getComponentName()
     */
    @Override
    public String getComponentName() {
	return "SiteWhere Load Test Node " + getVersion().getVersionIdentifier();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.server.lifecycle.LifecycleComponent#logState()
     */
    @Override
    public void logState() {
	getLogger().info("\n\nLoad Test Node State:\n" + logState("", this) + "\n");
    }

    /**
     * Verifies and loads the Spring configuration file.
     * 
     * @throws SiteWhereException
     */
    protected void initializeSpringContext() throws SiteWhereException {
	SERVER_SPRING_CONTEXT = getConfigurationResolver().resolveLoadTestContext(getVersion());
    }

    /**
     * Initialize server manager implementation.
     * 
     * @throws SiteWhereException
     */
    protected void initializeServerManager() throws SiteWhereException {
	try {
	    this.serverManager = (IServerManager) SERVER_SPRING_CONTEXT.getBean(ILoadTestBeans.BEAN_SERVER_MANAGER);
	    LOGGER.info("Server manager implementation using: " + serverManager.getClass().getName());
	} catch (NoSuchBeanDefinitionException e) {
	    throw new SiteWhereException("No SiteWhere connection information configured.");
	}
    }

    /**
     * Initialize agent manager implementation.
     * 
     * @throws SiteWhereException
     */
    protected void initializeAgentManager() throws SiteWhereException {
	try {
	    this.agentManager = (IAgentManager) SERVER_SPRING_CONTEXT.getBean(ILoadTestBeans.BEAN_AGENT_MANAGER);
	    LOGGER.info("Agent manager implementation using: " + agentManager.getClass().getName());
	} catch (NoSuchBeanDefinitionException e) {
	    throw new SiteWhereException("No agent manager implementation configured.");
	}
    }

    /**
     * Displays the server information banner in the log.
     */
    protected void showServerBanner() {
	String os = System.getProperty("os.name") + " (" + System.getProperty("os.version") + ")";
	String java = System.getProperty("java.vendor") + " (" + System.getProperty("java.version") + ")";

	// Print version information.
	List<String> messages = new ArrayList<String>();
	messages.add("SiteWhere Load Test Node");
	messages.add("");
	messages.add("Version: " + version.getVersionIdentifier() + "." + version.getBuildTimestamp());
	messages.add("Operating System: " + os);
	messages.add("Java Runtime: " + java);
	messages.add("");
	messages.add("Copyright (c) 2009-2017 SiteWhere, LLC");
	String message = Boilerplate.boilerplate(messages, "*");
	LOGGER.info("\n" + message + "\n");
    }

    /**
     * Get Spring application context for Atlas server objects.
     * 
     * @return
     */
    public static ApplicationContext getServerSpringContext() {
	return SERVER_SPRING_CONTEXT;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.loadtest.spi.ILoadTestServer#getVersion()
     */
    @Override
    public IVersion getVersion() {
	return version;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.loadtest.spi.server.ILoadTestServer#
     * getConfigurationResolver()
     */
    @Override
    public IConfigurationResolver getConfigurationResolver() {
	return configurationResolver;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.loadtest.spi.server.ILoadTestServer#getServerManager()
     */
    @Override
    public IServerManager getServerManager() {
	return serverManager;
    }

    public void setServerManager(IServerManager serverManager) {
	this.serverManager = serverManager;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.loadtest.spi.server.ILoadTestServer#getAgentManager()
     */
    @Override
    public IAgentManager getAgentManager() {
	return agentManager;
    }

    public void setAgentManager(IAgentManager agentManager) {
	this.agentManager = agentManager;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.spi.server.lifecycle.ILifecycleComponent#getLogger()
     */
    @Override
    public Logger getLogger() {
	return LOGGER;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.loadtest.spi.ILoadTestServer#getServerStartupError()
     */
    public ServerStartupException getServerStartupError() {
	return serverStartupError;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.sitewhere.loadtest.spi.ILoadTestServer#setServerStartupError(com.
     * sitewhere. spi.ServerStartupException)
     */
    public void setServerStartupError(ServerStartupException serverStartupError) {
	this.serverStartupError = serverStartupError;
    }
}