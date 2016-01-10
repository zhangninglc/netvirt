/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.ovsdb.openstack.netvirt.api;

import java.net.InetAddress;

/**
 * @author Josh Hershberg (jhershbe@redhat.com)
 */
public interface IcmpEchoProvider {

    Status programIcmpEchoEntry(Long dpid, String segmentationId,
                                 String macAddress, InetAddress ipAddress, Action action);
}
