/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSetBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class RemoteMcastCmd extends
        MergeCommand<RemoteMcastMacs, HwvtepGlobalAugmentationBuilder, HwvtepGlobalAugmentation> {

    public RemoteMcastCmd() {
    }

    @Override
    @Nullable
    public List<RemoteMcastMacs> getData(HwvtepGlobalAugmentation augmentation) {
        if (augmentation != null) {
            return augmentation.getRemoteMcastMacs();
        }
        return null;
    }

    @Override
    public void setData(HwvtepGlobalAugmentationBuilder builder, List<RemoteMcastMacs> data) {
        builder.setRemoteMcastMacs(data);
    }

    @Override
    public InstanceIdentifier<RemoteMcastMacs> generateId(InstanceIdentifier<Node> id, RemoteMcastMacs node) {
        HwvtepLogicalSwitchRef lsRef = HwvtepHAUtil.convertLogicalSwitchRef(node.key().getLogicalSwitchRef(), id);
        RemoteMcastMacsKey key = new RemoteMcastMacsKey(lsRef, node.getMacEntryKey());

        return id.augmentation(HwvtepGlobalAugmentation.class).child(RemoteMcastMacs.class, key);
    }

    @Override
    public RemoteMcastMacs transform(InstanceIdentifier<Node> nodePath, RemoteMcastMacs src) {
        RemoteMcastMacsBuilder ucmlBuilder = new RemoteMcastMacsBuilder(src);
        List<LocatorSet> locatorSet = new ArrayList<>();
        for (LocatorSet locator : src.nonnullLocatorSet()) {
            locatorSet.add(new LocatorSetBuilder().setLocatorRef(HwvtepHAUtil.buildLocatorRef(nodePath,
                    HwvtepHAUtil.getTepIpVal(locator.getLocatorRef()))).build());
        }
        ucmlBuilder.setLocatorSet(locatorSet);
        ucmlBuilder.setLogicalSwitchRef(HwvtepHAUtil.convertLogicalSwitchRef(src.getLogicalSwitchRef(), nodePath));
        ucmlBuilder.setMacEntryUuid(HwvtepHAUtil.getUUid(src.getMacEntryKey().getValue()));

        RemoteMcastMacsKey key = new RemoteMcastMacsKey(ucmlBuilder.getLogicalSwitchRef(),
                 ucmlBuilder.getMacEntryKey());
        ucmlBuilder.withKey(key);

        return ucmlBuilder.build();
    }

    @Override
    public Identifier getKey(RemoteMcastMacs data) {
        return data.key();
    }

    @Override
    public String getDescription() {
        return "RemoteMcastMacs";
    }

    @Override
    public boolean areEqual(RemoteMcastMacs updated, RemoteMcastMacs orig) {
        InstanceIdentifier<?> updatedMacRefIdentifier = updated.getLogicalSwitchRef().getValue();
        HwvtepNodeName updatedMacNodeName = updatedMacRefIdentifier.firstKeyOf(LogicalSwitches.class)
                .getHwvtepNodeName();
        InstanceIdentifier<?> origMacRefIdentifier = orig.getLogicalSwitchRef().getValue();
        HwvtepNodeName origMacNodeName = origMacRefIdentifier.firstKeyOf(LogicalSwitches.class).getHwvtepNodeName();
        if (Objects.equals(updated.getMacEntryKey(), orig.getMacEntryKey())
                && updatedMacNodeName.equals(origMacNodeName)) {
            List<LocatorSet> updatedLocatorSet = updated.getLocatorSet();
            List<LocatorSet> origLocatorSet = orig.getLocatorSet();
            if (!areSameSize(updatedLocatorSet, origLocatorSet)) {
                return false;
            }
            List<LocatorSet> added = diffOf(updatedLocatorSet, origLocatorSet, locatorSetComparator);
            if (!HwvtepHAUtil.isEmptyList(added)) {
                return false;
            }
            List<LocatorSet> removed = diffOf(origLocatorSet, updatedLocatorSet, locatorSetComparator);
            if (!HwvtepHAUtil.isEmptyList(removed)) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public RemoteMcastMacs withoutUuid(RemoteMcastMacs data) {
        return new RemoteMcastMacsBuilder(data).setMacEntryUuid(null).build();
    }
}
