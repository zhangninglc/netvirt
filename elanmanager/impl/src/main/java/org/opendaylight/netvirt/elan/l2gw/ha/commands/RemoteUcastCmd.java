/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.commands;

import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class RemoteUcastCmd extends MergeCommand<RemoteUcastMacs,
        HwvtepGlobalAugmentationBuilder, HwvtepGlobalAugmentation> {

    public RemoteUcastCmd() {
    }

    @Override
    @Nullable
    public List<RemoteUcastMacs> getData(HwvtepGlobalAugmentation node) {
        if (node != null) {
            return node.getRemoteUcastMacs();
        }
        return null;
    }

    @Override
    public void setData(HwvtepGlobalAugmentationBuilder builder, List<RemoteUcastMacs> data) {
        builder.setRemoteUcastMacs(data);
    }

    @Override
    public InstanceIdentifier<RemoteUcastMacs> generateId(InstanceIdentifier<Node> id, RemoteUcastMacs node) {
        HwvtepLogicalSwitchRef lsRef = HwvtepHAUtil.convertLogicalSwitchRef(node.key().getLogicalSwitchRef(), id);
        RemoteUcastMacsKey key = new RemoteUcastMacsKey(lsRef, node.getMacEntryKey());
        return id.augmentation(HwvtepGlobalAugmentation.class).child(RemoteUcastMacs.class, key);
    }

    @Override
    public RemoteUcastMacs transform(InstanceIdentifier<Node> nodePath, RemoteUcastMacs src) {
        RemoteUcastMacsBuilder ucmlBuilder = new RemoteUcastMacsBuilder(src);
        ucmlBuilder.setLocatorRef(HwvtepHAUtil.convertLocatorRef(src.getLocatorRef(), nodePath));
        ucmlBuilder.setLogicalSwitchRef(HwvtepHAUtil.convertLogicalSwitchRef(src.getLogicalSwitchRef(), nodePath));
        ucmlBuilder.setMacEntryUuid(HwvtepHAUtil.getUUid(src.getMacEntryKey().getValue()));

        RemoteUcastMacsKey key = new RemoteUcastMacsKey(ucmlBuilder.getLogicalSwitchRef(),
                ucmlBuilder.getMacEntryKey());
        ucmlBuilder.withKey(key);

        return ucmlBuilder.build();
    }

    @Override
    public Identifier getKey(RemoteUcastMacs data) {
        return data.key();
    }

    @Override
    public String getDescription() {
        return "RemoteUcastMacs";
    }

    @Override
    public boolean areEqual(RemoteUcastMacs updated, RemoteUcastMacs orig) {
        InstanceIdentifier<?> updatedMacRefIdentifier = updated.getLogicalSwitchRef().getValue();
        HwvtepNodeName updatedMacNodeName = updatedMacRefIdentifier.firstKeyOf(LogicalSwitches.class)
                .getHwvtepNodeName();
        InstanceIdentifier<?> origMacRefIdentifier = orig.getLogicalSwitchRef().getValue();
        HwvtepNodeName origMacNodeName = origMacRefIdentifier.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName();
        return Objects.equals(updated.getMacEntryKey(), orig.getMacEntryKey())
                && updatedMacNodeName.equals(origMacNodeName);
    }

    @Override
    public RemoteUcastMacs withoutUuid(RemoteUcastMacs data) {
        return new RemoteUcastMacsBuilder(data).setMacEntryUuid(null).build();
    }
}
