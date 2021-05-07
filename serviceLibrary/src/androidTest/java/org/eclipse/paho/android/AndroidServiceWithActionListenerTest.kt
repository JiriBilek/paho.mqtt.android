package org.eclipse.paho.android

import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.android.service.MqttService
import org.eclipse.paho.android.service.MqttServiceBinder
import org.eclipse.paho.client.mqttv3.*
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.any
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class AndroidServiceWithActionListenerTest {

    private val classCanonicalName = this.javaClass.canonicalName
    private var serverURI: String? = null
    private var mqttSSLServerURI: String? = null
    private var waitForCompletionTime = 0
    private lateinit var keyStorePwd: String

    //since we know tokens do not work when an action listener isn't specified
    private var notifier = TestCaseNotifier()

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Before
    @Throws(TimeoutException::class)
    fun setUp() {
        val properties = TestProperties(InstrumentationRegistry.getInstrumentation().targetContext)
        serverURI = properties.serverURI
        mqttSSLServerURI = properties.serverSSLURI
        waitForCompletionTime = properties.waitForCompletionTime
        keyStorePwd = properties.clientKeyStorePassword

        // Create the service Intent.
        val serviceIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            MqttService::class.java
        ).apply {
            // putExtra(SEED_KEY, 42L)
        }

        // Bind the service and grab a reference to the binder.
        val binder: IBinder = serviceRule.bindService(serviceIntent)

        // Get the reference to the service, to call public methods on the binder directly.
        val service: MqttService = (binder as MqttServiceBinder).service

        // Verify that the service is working correctly.
        assertThat(service.isTraceEnabled, `is`(any(Boolean::class.java)))
    }

    @Test
    fun testConnect() {
        val mqttClient: IMqttAsyncClient?
        mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "testConnect")
        mqttClient.connect(null, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        mqttClient.connect(null, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
    }

    @Test
    fun testRemoteConnect() {
        val methodName = "testRemoteConnect"
        val mqttClient: IMqttAsyncClient?
        mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "testRemoteConnect")
        mqttClient.connect(null, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        val mqttV3Receiver = MqttV3Receiver(mqttClient, null)
        mqttClient.setCallback(mqttV3Receiver)
        val mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.isCleanSession = false
        mqttClient.connect(mqttConnectOptions, null, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        val topicNames = arrayOf("$methodName/Topic")
        val topicQos = intArrayOf(0)
        mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        val payload = "Message payload $classCanonicalName.$methodName".toByteArray()
        mqttClient.publish(topicNames[0], payload, 1, false, null, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        val ok = mqttV3Receiver.validateReceipt(topicNames[0], 0, payload)
        if (!ok) {
            Assert.fail("Receive failed")
        }
        mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
    }

    @Test
    fun testLargeMessage() {
        notifier = TestCaseNotifier()
        val methodName = "testLargeMessage"
        var mqttClient: IMqttAsyncClient? = null
        try {
            mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "testLargeMessage")
            val mqttV3Receiver = MqttV3Receiver(mqttClient, null) //TODO do something about this?
            mqttClient.setCallback(mqttV3Receiver)
            mqttClient.connect(null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            val largeSize = 1000
            val topicNames = arrayOf("testLargeMessage" + "/Topic")
            val topicQos = intArrayOf(0)
            val message = ByteArray(largeSize)
            Arrays.fill(message, 's'.code.toByte())
            mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            mqttClient.unsubscribe(topicNames, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            mqttClient.publish(topicNames[0], message, 0, false, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            val ok = mqttV3Receiver.validateReceipt(topicNames[0], 0, message)
            if (!ok) {
                Assert.fail("Receive failed")
            }
        } catch (exception: Exception) {
            Assert.fail(
                "Failed to instantiate:" + methodName + " exception="
                        + exception
            )
        } finally {
            try {
                mqttClient!!.disconnect(null, ActionListener(notifier))
                notifier.waitForCompletion(waitForCompletionTime.toLong())
                mqttClient.close()
            } catch (ignored: Exception) {
            }
        }
    }

    @Test
    fun testMultipleClients() {
        val publishers = 2
        val subscribers = 5
        val methodName = "testMultipleClients"
        val mqttPublisher = arrayOfNulls<IMqttAsyncClient>(publishers)
        val mqttSubscriber = arrayOfNulls<IMqttAsyncClient>(subscribers)
        var disconnectToken: IMqttToken
        val topicNames = arrayOf("$methodName/Topic")
        val topicQos = intArrayOf(0)
        for (i in mqttPublisher.indices) {
            mqttPublisher[i] = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "MultiPub$i")
            mqttPublisher[i]!!.connect(null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
        } // for...
        val mqttV3Receiver = arrayOfNulls<MqttV3Receiver>(mqttSubscriber.size)
        for (i in mqttSubscriber.indices) {
            mqttSubscriber[i] = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "MultiSubscriber$i")
            mqttV3Receiver[i] = MqttV3Receiver(mqttSubscriber[i], null)
            mqttSubscriber[i]!!.setCallback(mqttV3Receiver[i]!!)
            mqttSubscriber[i]!!.connect(null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            mqttSubscriber[i]!!.subscribe(topicNames, topicQos, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
        } // for...
        for (iMessage in 0..1) {
            val payload = "Message $iMessage".toByteArray()
            for (aMqttPublisher in mqttPublisher) {
                aMqttPublisher!!.publish(topicNames[0], payload, 0, false, null, ActionListener(notifier))
                notifier.waitForCompletion(waitForCompletionTime.toLong())
            }
            TimeUnit.MILLISECONDS.sleep(8888)
            for (i in mqttSubscriber.indices) {
                for (aMqttPublisher in mqttPublisher) {
                    val ok = mqttV3Receiver[i]!!.validateReceipt(topicNames[0], 0, payload)
                    if (!ok) {
                        Assert.fail("Receive failed")
                    }
                } // for publishers...
            } // for subscribers...
        } // for messages...
        for (aMqttPublisher in mqttPublisher) {
            disconnectToken = aMqttPublisher!!.disconnect(null, null)
            disconnectToken.waitForCompletion(waitForCompletionTime.toLong())
            aMqttPublisher.close()
        }
        for (aMqttSubscriber in mqttSubscriber) {
            disconnectToken = aMqttSubscriber!!.disconnect(null, null)
            disconnectToken.waitForCompletion(waitForCompletionTime.toLong())
            aMqttSubscriber.close()
        }
    }

    @Test
    private fun testNonDurableSubs() {
        val methodName = "testNonDurableSubs"
        notifier = TestCaseNotifier()
        var mqttClient: IMqttAsyncClient?
        val connectToken: IMqttToken
        mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "testNonDurableSubs")
        var mqttV3Receiver = MqttV3Receiver(mqttClient, null)
        mqttClient.setCallback(mqttV3Receiver)
        var mqttConnectOptions = MqttConnectOptions()
        // Clean session true is the default and implies non durable
        // subscriptions.
        mqttConnectOptions.isCleanSession = true
        mqttClient.connect(mqttConnectOptions, null, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        val topicNames = arrayOf("$methodName/Topic")
        val topicQos = intArrayOf(2)
        mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        val payloadNotRetained = "Message payload $classCanonicalName.$methodName not retained".toByteArray()
        mqttClient.publish(topicNames[0], payloadNotRetained, 2, false, null, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        var ok = mqttV3Receiver.validateReceipt(topicNames[0], 2, payloadNotRetained)
        if (!ok) {
            Assert.fail("Receive failed")
        }

        // Retained publications.
        // ----------------------
        val payloadRetained = "Message payload $classCanonicalName.$methodName retained".toByteArray()
        mqttClient.publish(topicNames[0], payloadRetained, 2, true, null, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        ok = mqttV3Receiver.validateReceipt(topicNames[0], 2, payloadRetained)
        if (!ok) {
            Assert.fail("Receive failed")
        }

        // Check that unsubscribe and re subscribe resends the publication.
        mqttClient.unsubscribe(topicNames, null, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        ok = mqttV3Receiver.validateReceipt(topicNames[0], 2, payloadRetained)
        if (!ok) {
            Assert.fail("Receive failed")
        }

        // Check that subscribe without unsubscribe receives the
        // publication.
        mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        ok = mqttV3Receiver.validateReceipt(topicNames[0], 2, payloadRetained)
        if (!ok) {
            Assert.fail("Receive failed")
        }

        // Disconnect, reconnect and check that the retained publication is
        // still delivered.
        mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        mqttClient.close()
        mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "testNonDurableSubs")
        mqttV3Receiver = MqttV3Receiver(mqttClient, null)
        mqttClient.setCallback(mqttV3Receiver)
        mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.isCleanSession = true
        connectToken = mqttClient.connect(mqttConnectOptions, null, ActionListener(notifier))
        connectToken.waitForCompletion(1000)
        mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        ok = mqttV3Receiver.validateReceipt(
            topicNames[0], 2,
            payloadRetained
        )
        if (!ok) {
            Assert.fail("Receive failed")
        }
        mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
        notifier.waitForCompletion(1000)
        mqttClient.close()
    }

    @Suppress
    @Test
    fun testQoSPreserved() {
        val mqttClient: IMqttAsyncClient
        val methodName = "testQoSPreserved"
        mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "testQoSPreserved")
        val mqttV3Receiver = MqttV3Receiver(mqttClient, null)
        mqttClient.setCallback(mqttV3Receiver)
        mqttClient.connect(null, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        val topicNames = arrayOf("$methodName/Topic0", "$methodName/Topic1", "$methodName/Topic2")
        val topicQos = intArrayOf(0, 1, 2)
        mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
        for (i in topicNames.indices) {
            val message = ("Message payload " + classCanonicalName + "." + methodName + " " + topicNames[i]).toByteArray()
            for (iQos in 0..2) {
                mqttClient.publish(topicNames[i], message, iQos, false, null, null)
                notifier.waitForCompletion(waitForCompletionTime.toLong())
                val ok = mqttV3Receiver.validateReceipt(
                    topicNames[i],
                    min(iQos, topicQos[i]), message
                )
                if (!ok) {
                    Assert.fail("Receive failed sub Qos=" + topicQos[i] + " PublishQos=" + iQos)
                }
            }
        }
        mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
        notifier.waitForCompletion(waitForCompletionTime.toLong())
    }

    @Test
    fun testHAConnect() {
        val methodName = "testHAConnect"
        var client: IMqttAsyncClient? = null
        try {
            try {
                val junk = "tcp://junk:123"
                client = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, junk, methodName)
                val urls = arrayOf("tcp://junk", serverURI)
                val options = MqttConnectOptions()
                options.serverURIs = urls
                Log.i(methodName, "HA connect")
                client.connect(options, null, ActionListener(notifier))
                notifier.waitForCompletion(waitForCompletionTime.toLong())
                Log.i(methodName, "HA disconnect")
                client.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
                notifier.waitForCompletion(waitForCompletionTime.toLong())
                Log.i(methodName, "HA success")
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        } finally {
            client?.close()
        }
    }

    @Test
    fun testPubSub() {
        val methodName = "testPubSub"
        var mqttClient: IMqttAsyncClient? = null
        try {
            mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, methodName)
            val mqttV3Receiver = MqttV3Receiver(mqttClient, null)
            mqttClient.setCallback(mqttV3Receiver)
            mqttClient.connect(null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            val topicNames = arrayOf("testPubSub" + "/Topic")
            val topicQos = intArrayOf(0)
            val mqttMessage = MqttMessage("message for testPubSub".toByteArray())
            val message = mqttMessage.payload
            mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            mqttClient.publish(topicNames[0], message, 0, false, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            TimeUnit.MILLISECONDS.sleep(3000)
            val ok = mqttV3Receiver.validateReceipt(topicNames[0], 0, message)
            if (!ok) {
                Assert.fail("Receive failed")
            }
        } catch (exception: Exception) {
            Assert.fail(
                "Failed to instantiate:" + methodName + " exception="
                        + exception
            )
        } finally {
            try {
                mqttClient!!.disconnect(null, ActionListener(notifier))
                notifier.waitForCompletion(waitForCompletionTime.toLong())
                mqttClient.close()
            } catch (ignored: Exception) {
            }
        }
    }

    @Test
    fun testRetainedMessage() {
        val methodName = "testRetainedMessage"
        val mqttClient: IMqttAsyncClient?
        val mqttClientRetained: IMqttAsyncClient?
        try {
            mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, methodName)
            val mqttV3Receiver = MqttV3Receiver(mqttClient, null)
            mqttClient.setCallback(mqttV3Receiver)
            mqttClient.connect(null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            val topicNames = arrayOf("testRetainedMessage" + "/Topic")
            val topicQos = intArrayOf(0)
            val mqttMessage = MqttMessage("message for testPubSub".toByteArray())
            val message = mqttMessage.payload
            mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            mqttClient.publish(topicNames[0], message, 0, true, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            TimeUnit.MILLISECONDS.sleep(3000)
            var ok = mqttV3Receiver.validateReceipt(topicNames[0], 0, message)
            if (!ok) {
                Assert.fail("Receive failed")
            }
            Log.i(methodName, "First client received message successfully")
            mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            mqttClient.close()
            mqttClientRetained = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, serverURI!!, "Retained")
            Log.i(methodName, "New MqttAndroidClient mqttClientRetained")
            val mqttV3ReceiverRetained = MqttV3Receiver(mqttClientRetained, null)
            mqttClientRetained.setCallback(mqttV3ReceiverRetained)
            Log.i(methodName, "Assigning callback...")
            mqttClientRetained.connect(null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            Log.i(methodName, "Connect to mqtt server")
            mqttClientRetained.subscribe(topicNames, topicQos, null, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            Log.i(methodName, "subscribe " + topicNames[0] + " QoS is " + topicQos[0])
            TimeUnit.MILLISECONDS.sleep(3000)
            ok = mqttV3ReceiverRetained.validateReceipt(topicNames[0], 0, message)
            if (!ok) {
                Assert.fail("Receive retained message failed")
            }
            Log.i(methodName, "Second client received message successfully")
            mqttClientRetained.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
            notifier.waitForCompletion(waitForCompletionTime.toLong())
            mqttClientRetained.close()
        } catch (exception: Exception) {
            Assert.fail("Failed to instantiate:$methodName exception=$exception")
        }
    }

    /**
     * Tests that a client can be constructed and that it can connect to and disconnect from the service via SSL
     */
    @Suppress
    @Test
    fun testSSLConnect() {
        var mqttClient: MqttAndroidClient? = null
        try {
            mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, mqttSSLServerURI!!, "testSSLConnect")
            val options = MqttConnectOptions()
            options.socketFactory =
                mqttClient.getSSLSocketFactory(InstrumentationRegistry.getInstrumentation().targetContext.assets.open("test.bks"), keyStorePwd)
            var connectToken = mqttClient.connect(options, InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
            connectToken.waitForCompletion(waitForCompletionTime.toLong())
            var disconnectToken: IMqttToken =
                mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
            disconnectToken.waitForCompletion(waitForCompletionTime.toLong())
            connectToken = mqttClient.connect(options, InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
            connectToken.waitForCompletion(waitForCompletionTime.toLong())
            disconnectToken = mqttClient.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
            disconnectToken.waitForCompletion(waitForCompletionTime.toLong())
        } catch (exception: Exception) {
            Assert.fail("Failed:testSSLConnect exception=$exception")
        } finally {
            mqttClient?.close()
        }
    }

    /**
     * An SSL connection with server cert authentication, simple pub/sub of an message
     */
    @Suppress
    @Test
    fun testSSLPubSub() {
        var mqttClient: MqttAndroidClient? = null
        val connectToken: IMqttToken?
        val disconnectToken: IMqttToken?
        val subToken: IMqttToken?
        val pubToken: IMqttDeliveryToken?
        try {
            mqttClient = MqttAndroidClient(InstrumentationRegistry.getInstrumentation().targetContext, mqttSSLServerURI!!, "testSSLPubSub")
            val options = MqttConnectOptions()
            options.socketFactory =
                mqttClient.getSSLSocketFactory(InstrumentationRegistry.getInstrumentation().targetContext.assets.open("test.bks"), keyStorePwd)
            val mqttV3Receiver = MqttV3Receiver(mqttClient, null)
            mqttClient.setCallback(mqttV3Receiver)
            connectToken = mqttClient.connect(options, InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
            connectToken.waitForCompletion(waitForCompletionTime.toLong())
            val topicNames = arrayOf("testSSLPubSub" + "/Topic")
            val topicQos = intArrayOf(0)
            val mqttMessage = MqttMessage("message for testSSLPubSub".toByteArray())
            val message = mqttMessage.payload
            subToken = mqttClient.subscribe(topicNames, topicQos, null, ActionListener(notifier))
            subToken.waitForCompletion(waitForCompletionTime.toLong())
            pubToken = mqttClient.publish(topicNames[0], message, 0, false, null, ActionListener(notifier))
            pubToken.waitForCompletion(waitForCompletionTime.toLong())
            TimeUnit.MILLISECONDS.sleep(6000)
            val ok = mqttV3Receiver.validateReceipt(topicNames[0], 0, message)
            if (!ok) {
                Assert.fail("Receive failed")
            }
        } catch (exception: Exception) {
            Assert.fail("Failed:testSSLPubSub exception=$exception")
        } finally {
            disconnectToken = mqttClient!!.disconnect(InstrumentationRegistry.getInstrumentation().targetContext, ActionListener(notifier))
            disconnectToken.waitForCompletion(waitForCompletionTime.toLong())
            mqttClient.close()
        }
    }

    private inner class ActionListener(private val notifier: TestCaseNotifier) : IMqttActionListener {

        override fun onFailure(token: IMqttToken, exception: Throwable) {
            notifier.storeException(exception)
            Assert.fail("onFailure")
        }

        override fun onSuccess(token: IMqttToken) {
            Log.d(TAG, "onSuccess")
        }
    }

    companion object {
        private val classCanonicalName = this::class.java.canonicalName
        private val TAG = classCanonicalName
    }
}