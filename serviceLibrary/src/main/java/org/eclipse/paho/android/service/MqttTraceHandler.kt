/*******************************************************************************
 * Copyright (c) 1999, 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.eclipse.paho.android.service

/**
 * Interface for simple trace handling, pass the trace message to trace callback.
 */
interface MqttTraceHandler {
    /**
     * Trace debugging information
     *
     * @param message the text to be traced
     */
    fun traceDebug(message: String?)

    /**
     * Trace error information
     *
     * @param message the text to be traced
     */
    fun traceError(message: String?)

    /**
     * trace exceptions
     *
     * @param message the text to be traced
     * @param e       the exception
     */
    fun traceException(message: String?, e: Exception?)
}