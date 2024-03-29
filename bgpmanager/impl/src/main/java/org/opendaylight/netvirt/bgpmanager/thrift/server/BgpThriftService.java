/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.thrift.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.ServerContext;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServerEventHandler;
import org.apache.thrift.server.TThreadedSelectorServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.BgpUpdater;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpThriftService {
    private static final Logger LOG = LoggerFactory.getLogger(BgpThriftService.class);

    private final InetSocketAddress bgpThriftServerSocketAddr;
    private final IBgpManager bgpManager;
    private final BgpConfigurationManager bgpConfigManager;
    private volatile TServer server;
    private static ThreadFactory thriftServerThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("bgp-thrift-server-%d").build();
    private static ExecutorService threadPool = Executors.newFixedThreadPool(1, thriftServerThreadFactory);
    private volatile Future ft;

    public BgpThriftService(InetSocketAddress bgpThriftServerSocketAddr, IBgpManager bm,
                            BgpConfigurationManager bgpConfigManager) {
        this.bgpThriftServerSocketAddr = bgpThriftServerSocketAddr;
        bgpManager = bm;
        this.bgpConfigManager = bgpConfigManager;
    }

    public static class ThriftClientContext implements ServerContext {
        TProtocol in;

        public ThriftClientContext(TProtocol in) {
            this.in = in;
        }

        public TProtocol getIn() {
            return in;
        }
    }

    public class BgpUpdateServer implements Runnable, BgpUpdater.Iface {

        ThriftClientContext oldThriftClientContext;

        BgpUpdateServer() {
        }

        @Override
        public void run() {
            try {
                BgpUpdater.Processor processor = new BgpUpdater.Processor(this);

                TNonblockingServerTransport trans = new TNonblockingServerSocket(bgpThriftServerSocketAddr);
                TThreadedSelectorServer.Args args = new TThreadedSelectorServer.Args(trans);
                args.transportFactory(new TFramedTransport.Factory());
                args.protocolFactory(new TBinaryProtocol.Factory());
                args.processor(processor);
                args.selectorThreads(1);
                args.workerThreads(1);
                server = new TThreadedSelectorServer(args);
                server.setServerEventHandler(new TServerEventHandler() {
                    @Override
                    public void preServe() {
                        LOG.info("Bgp thrift server pre serve event");
                    }

                    @Override
                    public ServerContext createContext(TProtocol input, TProtocol output) {
                        LOG.info("Bgp thrift server create context event");
                        synchronized (this) {
                            if (oldThriftClientContext != null) {
                                LOG.info("Bgp thrift server closing old context");
                                oldThriftClientContext.getIn().getTransport().close();
                            } else {
                                LOG.info("Bgp thrift server old context is null nothing to close");
                            }
                            oldThriftClientContext = new ThriftClientContext(input);
                            return oldThriftClientContext;
                        }
                    }

                    @Override
                    public void deleteContext(ServerContext serverContext, TProtocol input, TProtocol output) {
                        LOG.info("Bgp thrift server delete context event");
                        if (oldThriftClientContext == serverContext) {
                            LOG.info("Bgp thrift server cleanup old context");
                            oldThriftClientContext = null;
                        } else {
                            LOG.info("Bgp thrift server cleanup context");
                        }
                    }

                    @Override
                    public void processContext(ServerContext serverContext, TTransport inputTransport,
                            TTransport outputTransport) {
                        LOG.trace("Bgp thrift server process context event");
                    }
                });
                server.serve();
            } catch (TTransportException e) {
                LOG.error("Exception in BGP Updater server", e);
            }
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public void onUpdatePushRoute(protocol_type protocolType,
                                      String rd,
                                      String prefix,
                                      int plen,
                                      String nexthop,
                                      long ethtag,
                                      String esi,
                                      String macaddress,
                                      int l3label,
                                      int l2label,
                                      String routermac,
                                      af_afi afi) {
            LOG.debug("Update on push route : rd {} prefix {} plen {}", rd, prefix, plen);

            // l2label is ignored even in case of RT5. only l3label considered
            bgpConfigManager.onUpdatePushRoute(
                    protocolType,
                    rd,
                    prefix,
                    plen,
                    nexthop,
                    macaddress,
                    l3label,
                    l2label,
                    routermac,
                    afi);
        }

        @Override
        public void onUpdateWithdrawRoute(protocol_type protocolType,
                                          String rd,
                                          String prefix,
                                          int plen,
                                          String nexthop,
                                          long ethtag,
                                          String esi,
                                          String macaddress,
                                          int l3label,
                                          int l2label,
                                          af_afi afi) {
            LOG.debug("Route del ** {} ** {}/{} ", rd, prefix, plen);
            bgpConfigManager.onUpdateWithdrawRoute(
                    protocolType,
                    rd,
                    prefix,
                    plen,
                    nexthop,
                    macaddress);
        }

        @Override
        public void peerDown(String ipAddress, long asNumber) {
            LOG.info("PeerDown {} : {} From BGP", ipAddress, asNumber);
            bgpConfigManager.peerDown(ipAddress, asNumber);
        }

        @Override
        public void peerUp(String ipAddress, long asNumber) {
            LOG.info("PeerUp {} : {} from BGP", ipAddress, asNumber);
            bgpConfigManager.peerUp(ipAddress, asNumber);
        }

        @Override
        public void onStartConfigResyncNotification() {
            LOG.info("BGP (re)started");
            bgpManager.setQbgprestartTS(System.currentTimeMillis());
            bgpManager.bgpRestarted();
        }

        @Override
        public void onNotificationSendEvent(String prefix, byte errCode,
                byte errSubcode) {
            bgpManager.sendNotificationEvent((int) errCode, (int) errSubcode);
        }
    }

    public synchronized void start() {
        if (isBgpThriftServiceStarted()) {
            stop();
        }
        ft = threadPool.submit(new BgpUpdateServer());
    }

    public boolean isBgpThriftServiceStarted() {
        return ((ft == null) ? false : true);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (ft != null && !ft.isDone()) {
            ft.cancel(true);
        }
        ft = null;
    }
}
