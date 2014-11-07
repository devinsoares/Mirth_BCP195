/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.dcm4che2.data.UID;
import org.dcm4che2.tool.dcmrcv.DcmRcv;
import org.dcm4che2.tool.dcmrcv.MirthDcmRcv;

import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.util.MirthSSLUtil;

public class DICOMReceiver extends SourceConnector {
    private Logger logger = Logger.getLogger(this.getClass());
    private DICOMReceiverProperties connectorProperties;
    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
    private DcmRcv dcmrcv;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        this.connectorProperties = (DICOMReceiverProperties) getConnectorProperties();

        dcmrcv = new MirthDcmRcv(this);
    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    @Override
    public void onStart() throws ConnectorTaskException {
        try {
            dcmrcv.setPort(NumberUtils.toInt(connectorProperties.getListenerConnectorProperties().getPort()));
            dcmrcv.setHostname(connectorProperties.getListenerConnectorProperties().getHost());
            dcmrcv.setAEtitle("DCMRCV");

            String[] only_def_ts = { UID.ImplicitVRLittleEndian };
            String[] native_le_ts = { UID.ImplicitVRLittleEndian };
            String[] native_ts = { UID.ImplicitVRLittleEndian };
            String[] non_retired_ts = { UID.ImplicitVRLittleEndian };

            if (StringUtils.isNotBlank(connectorProperties.getDest())) {
                dcmrcv.setDestination(connectorProperties.getDest());
            }

            if (connectorProperties.isDefts()) {
                dcmrcv.setTransferSyntax(only_def_ts);
            } else if (connectorProperties.isNativeData()) {
                if (connectorProperties.isBigEndian()) {
                    dcmrcv.setTransferSyntax(native_ts);
                } else {
                    dcmrcv.setTransferSyntax(native_le_ts);
                }
            } else if (connectorProperties.isBigEndian()) {
                dcmrcv.setTransferSyntax(non_retired_ts);
            }

            if (StringUtils.isNotBlank(connectorProperties.getApplicationEntity())) {
                dcmrcv.setAEtitle(connectorProperties.getApplicationEntity());
            }

            //TODO Allow variables
            int value = NumberUtils.toInt(connectorProperties.getReaper());
            if (value != 10) {
                dcmrcv.setAssociationReaperPeriod(value);
            }

            value = NumberUtils.toInt(connectorProperties.getIdleTo());
            if (value != 60) {
                dcmrcv.setIdleTimeout(value);
            }

            value = NumberUtils.toInt(connectorProperties.getRequestTo());
            if (value != 5) {
                dcmrcv.setRequestTimeout(value);
            }

            value = NumberUtils.toInt(connectorProperties.getReleaseTo());
            if (value != 5) {
                dcmrcv.setReleaseTimeout(value);
            }

            value = NumberUtils.toInt(connectorProperties.getSoCloseDelay());
            if (value != 50) {
                dcmrcv.setSocketCloseDelay(value);
            }

            value = NumberUtils.toInt(connectorProperties.getRspDelay());
            if (value > 0) {
                dcmrcv.setDimseRspDelay(value);
            }

            value = NumberUtils.toInt(connectorProperties.getRcvpdulen());
            if (value != 16) {
                dcmrcv.setMaxPDULengthReceive(value);
            }

            value = NumberUtils.toInt(connectorProperties.getSndpdulen());
            if (value != 16) {
                dcmrcv.setMaxPDULengthSend(value);
            }

            value = NumberUtils.toInt(connectorProperties.getSosndbuf());
            if (value > 0) {
                dcmrcv.setSendBufferSize(value);
            }

            value = NumberUtils.toInt(connectorProperties.getSorcvbuf());
            if (value > 0) {
                dcmrcv.setReceiveBufferSize(value);
            }

            value = NumberUtils.toInt(connectorProperties.getBufSize());
            if (value != 1) {
                dcmrcv.setFileBufferSize(value);
            }

            dcmrcv.setPackPDV(connectorProperties.isPdv1());
            dcmrcv.setTcpNoDelay(!connectorProperties.isTcpDelay());

            value = NumberUtils.toInt(connectorProperties.getAsync());
            if (value > 0) {
                dcmrcv.setMaxOpsPerformed(value);
            }

            dcmrcv.initTransferCapability();

            // connection tls settings

            if (!StringUtils.equals(connectorProperties.getTls(), "notls")) {
                if (connectorProperties.getTls().equals("without")) {
                    dcmrcv.setTlsWithoutEncyrption();
                } else if (connectorProperties.getTls().equals("3des")) {
                    dcmrcv.setTls3DES_EDE_CBC();
                } else if (connectorProperties.getTls().equals("aes")) {
                    dcmrcv.setTlsAES_128_CBC();
                }

                if (StringUtils.isNotBlank(connectorProperties.getTrustStore())) {
                    dcmrcv.setTrustStoreURL(connectorProperties.getTrustStore());
                }

                if (StringUtils.isNotBlank(connectorProperties.getTrustStorePW())) {
                    dcmrcv.setTrustStorePassword(connectorProperties.getTrustStorePW());
                }

                if (StringUtils.isNotBlank(connectorProperties.getKeyPW())) {
                    dcmrcv.setKeyPassword(connectorProperties.getKeyPW());
                }

                if (StringUtils.isNotBlank(connectorProperties.getKeyStore())) {
                    dcmrcv.setKeyStoreURL(connectorProperties.getKeyStore());
                }

                if (StringUtils.isNotBlank(connectorProperties.getKeyStorePW())) {
                    dcmrcv.setKeyStorePassword(connectorProperties.getKeyStorePW());
                }

                dcmrcv.setTlsNeedClientAuth(connectorProperties.isNoClientAuth());

                String[] protocols = configurationController.getHttpsProtocols();

                if (connectorProperties.isNossl2()) {
                    if (ArrayUtils.contains(protocols, "SSLv2Hello")) {
                        List<String> protocolsList = new ArrayList<String>(Arrays.asList(protocols));
                        protocolsList.remove("SSLv2Hello");
                        protocols = protocolsList.toArray(new String[protocolsList.size()]);
                    }
                } else if (!ArrayUtils.contains(protocols, "SSLv2Hello")) {
                    List<String> protocolsList = new ArrayList<String>(Arrays.asList(protocols));
                    protocolsList.add("SSLv2Hello");
                    protocols = protocolsList.toArray(new String[protocolsList.size()]);
                }

                dcmrcv.setTlsProtocol(MirthSSLUtil.getEnabledHttpsProtocols(protocols));

                dcmrcv.initTLS();
            }

            // start the DICOM port
            dcmrcv.start();

            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getSourceName(), ConnectionStatusEventType.IDLE));
        } catch (Exception e) {
            throw new ConnectorTaskException("Failed to start DICOM Listener", e);
        }
    }

    @Override
    public void onStop() throws ConnectorTaskException {
        try {
            dcmrcv.stop();
        } catch (Exception e) {
            logger.error("Unable to close DICOM port.", e);
        } finally {
            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getSourceName(), ConnectionStatusEventType.DISCONNECTED));
        }

        logger.debug("closed DICOM port");
    }

    @Override
    public void onHalt() throws ConnectorTaskException {
        onStop();
    }

    @Override
    public void handleRecoveredResponse(DispatchResult dispatchResult) {
        finishDispatch(dispatchResult);
    }

}