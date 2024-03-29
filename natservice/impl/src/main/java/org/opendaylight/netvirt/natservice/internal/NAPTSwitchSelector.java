/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NAPTSwitchSelector {
    private static final Logger LOG = LoggerFactory.getLogger(NAPTSwitchSelector.class);
    private final DataBroker dataBroker;

    @Inject
    public NAPTSwitchSelector(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    BigInteger selectNewNAPTSwitch(String routerName) {
        LOG.info("selectNewNAPTSwitch : Select a new NAPT switch for router {}", routerName);
        Map<BigInteger, Integer> naptSwitchWeights = constructNAPTSwitches();
        List<BigInteger> routerSwitches = getDpnsForVpn(routerName);
        if (routerSwitches.isEmpty()) {
            LOG.warn("selectNewNAPTSwitch : Delaying NAPT switch selection due to no dpns scenario for router {}",
                    routerName);
            return BigInteger.ZERO;
        }

        Set<SwitchWeight> switchWeights = new TreeSet<>();
        for (BigInteger dpn : routerSwitches) {
            if (naptSwitchWeights.get(dpn) != null) {
                switchWeights.add(new SwitchWeight(dpn, naptSwitchWeights.get(dpn)));
            } else {
                switchWeights.add(new SwitchWeight(dpn, 0));
            }
        }

        BigInteger primarySwitch;

        if (!switchWeights.isEmpty()) {

            LOG.debug("selectNewNAPTSwitch : Current switch weights for router {} - {}", routerName, switchWeights);

            RouterToNaptSwitchBuilder routerToNaptSwitchBuilder =
                new RouterToNaptSwitchBuilder().setRouterName(routerName);
            SwitchWeight firstSwitchWeight = switchWeights.iterator().next();
            primarySwitch = firstSwitchWeight.getSwitch();
            RouterToNaptSwitch id = routerToNaptSwitchBuilder.setPrimarySwitchId(primarySwitch).build();

            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                getNaptSwitchesIdentifier(routerName), id);

            LOG.debug("selectNewNAPTSwitch : successful addition of RouterToNaptSwitch to napt-switches container");
            return primarySwitch;
        } else {
            primarySwitch = BigInteger.ZERO;

            LOG.debug("selectNewNAPTSwitch : switchWeights empty, primarySwitch: {} ", primarySwitch);
            return primarySwitch;
        }
    }

    private Map<BigInteger, Integer> constructNAPTSwitches() {
        Optional<NaptSwitches> optNaptSwitches =
            MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, getNaptSwitchesIdentifier());
        Map<BigInteger, Integer> switchWeights = new HashMap<>();

        if (optNaptSwitches.isPresent()) {
            NaptSwitches naptSwitches = optNaptSwitches.get();

            for (RouterToNaptSwitch naptSwitch : naptSwitches.nonnullRouterToNaptSwitch()) {
                BigInteger primarySwitch = naptSwitch.getPrimarySwitchId();
                //update weight
                Integer weight = switchWeights.get(primarySwitch);
                if (weight == null) {
                    switchWeights.put(primarySwitch, 1);
                } else {
                    switchWeights.put(primarySwitch, ++weight);
                }
            }
        }
        return switchWeights;
    }

    private InstanceIdentifier<NaptSwitches> getNaptSwitchesIdentifier() {
        return InstanceIdentifier.create(NaptSwitches.class);
    }

    private InstanceIdentifier<RouterToNaptSwitch> getNaptSwitchesIdentifier(String routerName) {
        return InstanceIdentifier.builder(NaptSwitches.class)
            .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
    }

    @NonNull
    public List<BigInteger> getDpnsForVpn(String routerName) {
        LOG.debug("getDpnsForVpn: called for RouterName {}", routerName);
        long bgpVpnId = NatUtil.getBgpVpnId(dataBroker, routerName);
        // TODO Why?
        if (bgpVpnId != NatConstants.INVALID_ID) {
            return NatUtil.getDpnsForRouter(dataBroker, routerName);
        }
        return NatUtil.getDpnsForRouter(dataBroker, routerName);
    }

    private static class SwitchWeight implements Comparable<SwitchWeight> {
        private final BigInteger swich;
        private int weight;

        SwitchWeight(BigInteger swich, int weight) {
            this.swich = swich;
            this.weight = weight;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (swich == null ? 0 : swich.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            SwitchWeight other = (SwitchWeight) obj;
            if (swich == null) {
                if (other.swich != null) {
                    return false;
                }
            } else if (!swich.equals(other.swich)) {
                return false;
            }
            return true;
        }

        public BigInteger getSwitch() {
            return swich;
        }

        public int getWeight() {
            return weight;
        }

        @Override
        public int compareTo(@NonNull SwitchWeight switchWeight) {
            return weight - switchWeight.getWeight();
        }
    }
}
