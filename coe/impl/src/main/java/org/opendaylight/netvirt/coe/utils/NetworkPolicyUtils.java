/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.utils;

import com.google.common.collect.ImmutableBiMap;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.core.rev181205.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.meta.v1.rev181205.label.selector.MatchLabels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.meta.v1.rev181205.label.selector.MatchLabelsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.PolicyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.ip.block.IpBlock;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.ip.block.IpBlockBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.NetworkPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.NetworkPolicyEgressRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.NetworkPolicyEgressRuleBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.network.policy.egress.rule.EgressPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.network.policy.egress.rule.EgressPortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.network.policy.egress.rule.To;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.network.policy.egress.rule.ToBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.NetworkPolicyIngressRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.NetworkPolicyIngressRuleBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.network.policy.ingress.rule.From;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.network.policy.ingress.rule.FromBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.network.policy.ingress.rule.IngressPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.network.policy.ingress.rule.IngressPortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.peer.NetworkPolicyPeer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.peer.NetworkPolicyPeerBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.port.NetworkPolicyPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.port.NetworkPolicyPortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.NetworkPolicySpec;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.NetworkPolicySpecBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.Egress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.EgressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.Ingress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.IngressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.PodSelector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.PodSelectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.rev181205.K8s;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class NetworkPolicyUtils {
    public static final ImmutableBiMap<Protocol, Short> PROTOCOL_MAP = ImmutableBiMap.of(
        Protocol.TCP, (short)6,
        Protocol.UDP, (short)17,
        Protocol.SCTP, (short)132
    );

    private NetworkPolicyUtils() {}

    @NonNull
    public static MatchLabels buildMatchLabels(@NonNull String key, @NonNull String value) {
        return new MatchLabelsBuilder().setKey(key).setValue(value).build();
    }

    @NonNull
    public static PodSelector buildPodSelector(@NonNull List<MatchLabels> matchLabels) {
        return new PodSelectorBuilder().setMatchLabels(matchLabels).build();
    }

    @NonNull
    public static InstanceIdentifier<NetworkPolicy> getNetworkPolicyIid(@NonNull String uuid) {
        return InstanceIdentifier.create(K8s.class).child(NetworkPolicies.class)
            .child(NetworkPolicy.class, new NetworkPolicyKey(new Uuid(uuid)));
    }

    @NonNull
    public static IpBlock buildIpBlock(@NonNull String cidr, @Nullable List<String> except) {
        IpBlockBuilder ipBlockBuilder = new IpBlockBuilder().setCidr(cidr);

        if (except != null && !except.isEmpty()) {
            ipBlockBuilder.setExcept(except);
        }

        return new IpBlockBuilder().setCidr(cidr).setExcept(except).build();
    }

    // TODO add pod and namespace selector handling
    @NonNull
    public static NetworkPolicyPeer buildNetworkPolicyPeer(@NonNull IpBlock ipBlock) {
        return new NetworkPolicyPeerBuilder().setIpBlock(ipBlock).build();
    }

    @NonNull
    public static NetworkPolicyPort buildNetworkPolicyPort(@NonNull String port, @NonNull Protocol protocol) {
        return new NetworkPolicyPortBuilder().setPort(port).setProtocol(protocol).build();
    }

    @NonNull
    public static IngressPorts buildIngressPorts(@NonNull NetworkPolicyPort port) {
        return new IngressPortsBuilder().setNetworkPolicyPort(port).build();
    }

    @NonNull
    public static From buildFrom(@NonNull NetworkPolicyPeer peer) {
        return new FromBuilder().setNetworkPolicyPeer(peer).build();
    }

    @NonNull
    public static EgressPorts buildEgressPorts(@NonNull NetworkPolicyPort port) {
        return new EgressPortsBuilder().setNetworkPolicyPort(port).build();
    }

    @NonNull
    public static To buildTo(@NonNull NetworkPolicyPeer peer) {
        return new ToBuilder().setNetworkPolicyPeer(peer).build();
    }

    @NonNull
    public static NetworkPolicyIngressRule buildNetworkPolicyIngressRule(@Nullable List<IngressPorts> ports,
                                                                         @Nullable List<From> fromList) {

        NetworkPolicyIngressRuleBuilder networkPolicyIngressRuleBuilder = new NetworkPolicyIngressRuleBuilder();

        if (ports != null && !ports.isEmpty()) {
            networkPolicyIngressRuleBuilder.setIngressPorts(ports);
        }
        if (fromList != null && !fromList.isEmpty()) {
            networkPolicyIngressRuleBuilder.setFrom(fromList);
        }

        return networkPolicyIngressRuleBuilder.build();
    }

    @NonNull
    public static NetworkPolicyEgressRule buildNetworkPolicyEgressRule(@Nullable List<EgressPorts> ports,
                                                                       @Nullable List<To> toList) {

        NetworkPolicyEgressRuleBuilder networkPolicyEgressRuleBuilder = new NetworkPolicyEgressRuleBuilder();

        if (ports != null && !ports.isEmpty()) {
            networkPolicyEgressRuleBuilder.setEgressPorts(ports);
        }
        if (toList != null && !toList.isEmpty()) {
            networkPolicyEgressRuleBuilder.setTo(toList);
        }

        return networkPolicyEgressRuleBuilder.build();
    }

    @NonNull
    public static Ingress buildIngress(@NonNull NetworkPolicyIngressRule rule) {
        return new IngressBuilder().setNetworkPolicyIngressRule(rule).build();
    }

    @NonNull
    public static Egress buildEgress(@NonNull NetworkPolicyEgressRule rule) {
        return new EgressBuilder().setNetworkPolicyEgressRule(rule).build();
    }

    @NonNull
    public static NetworkPolicySpec buildNetworkPolicySpec(@NonNull PodSelector podSelector,
                                                           @Nullable List<Ingress> ingress,
                                                           @Nullable List<Egress> egress,
                                                           @Nullable List<PolicyType> policyTypes) {
        NetworkPolicySpecBuilder networkPolicySpecBuilder = new NetworkPolicySpecBuilder().setPodSelector(podSelector);

        if (ingress != null && !ingress.isEmpty()) {
            networkPolicySpecBuilder.setIngress(ingress);
        }
        if (egress != null && !egress.isEmpty()) {
            networkPolicySpecBuilder.setEgress(egress);
        }
        if (policyTypes != null && !policyTypes.isEmpty()) {
            networkPolicySpecBuilder.setPolicyTypes(policyTypes);
        }

        return networkPolicySpecBuilder.build();
    }

    @NonNull
    public static NetworkPolicy buildNetworkPolicy(@NonNull String uuid, @Nullable String name,
                                                   @Nullable NetworkPolicySpec spec) {
        NetworkPolicyBuilder networkPolicyBuilder = new NetworkPolicyBuilder().setUuid(new Uuid(uuid));
        if (name != null) {
            networkPolicyBuilder.setName(name);
        }
        if (spec != null) {
            networkPolicyBuilder.setNetworkPolicySpec(spec);
        }

        return networkPolicyBuilder.build();
    }
}
