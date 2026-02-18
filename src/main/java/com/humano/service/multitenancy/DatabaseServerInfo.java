package com.humano.service.multitenancy;

/**
 * Record to hold database server information.
 *
 * @param host the database server hostname
 * @param port the database server port
 * @param region the geographic region
 * @param serverGroup the server cluster/group identifier
 * @param dedicated whether this is a dedicated server
 *
 * @author Humano Team
 */
public record DatabaseServerInfo(String host, int port, String region, String serverGroup, boolean dedicated) {}
