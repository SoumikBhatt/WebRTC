package com.madroid.webrtc

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

//
// Created by Soumik on 4/22/2021.
// piyal.developer@gmail.com
//

internal open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(s: String) {}
    override fun onSetFailure(s: String) {}
}