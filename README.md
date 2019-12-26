# XBeeUartMQTTProxy
A TCP/IP Proxy to tunnel MQTT Messages over UART using a [XBee Device](https://www.digi.com/xbee) implemented in Java

## Intended Audience
This implementation is mainly targeted at developers who work in the IoT field albeit this should work out of the box it is not meant to be used by end users. The reason is that it may be neccessary to adjust some of the parameters (timeout values for example) to meet the requirments in your field. Second I use the XBee API in combination with the DigiMesh Devices if you want to use this software for example with other Digi Devices such like celluar you must adjust it to suit your needs. I use the API in normal mode so for those whoo need to use the explicit format it also needs adjustments by you.

## Libraries needed

 - [xbee-java-library-1.3.0.jar](https://github.com/digidotcom/xbee-java/releases)
 - [slf4j-api-1.7.12.jar](http://www.slf4j.org/download.html)
 - [rxtx-2.2.jar](http://rxtx.qbang.org/) (remember you also need the JNI native library for your operating system)

Optional (if you want to see logging output from the XBee Device)

 - slf4j-simple-1.7.12.jar

There is no strict need on the version numbers, this are the versions which I used - which were available of the time of this writing -

## Introduction

Imagine you got a network of about let's say 50 sensors all connected with XBee Devices and you want to benefit from using the MQTT protocol then there is one little drawback - MQTT relies soley on TCP/IP - which you do not have on a DigiMesh consisting just of XBee Devices. So instead of writing your own implementation of the MQTT protocol to overcome this drawback the "easiest" thing would be (and easiest is not written in quotes by chance :-) to use a TCP/IP proxy listening for data from your sensor and forward this data over UART with a XBee device to the broker. This is where this implementation comes in. You have a local proxy on your sensor device **UARTProxy** listening for data and you have another device which is attached to the network (the Gateway) running **UARTProxyGateway** forwarding the data to the broker. This implementation also handles some of the obstacles arising with the use of processing data by radio frequency over the air, first it happens that packets overtake each other and arive the receiver not in the order sent, second on the gateway proxy it is not guaranteed that there are no other packets from other devices arriving during a submission of a MQTT message by one XBee Device. For these problems the implementation has built in support for packet sequencing to prevent the problems with overtaking and built in support for multiplexing to overcome the problem that other devices may sent in parallel.

## Status

I've tested this implementation in my laboratory and it runs well with 3 Devices sending consecutive MQTT Messages in parallel, however it has not been tested in the field yet with more devices under real conditions

## Limitations

You cannot send more than 2147483647 packets in one connection (**Note:** this is the number of packets not the number of MQTT messages, how many packets a MQTT packet accomplishes depends on various settings such as the QoS level used)

## Usage

```sh
java -jar UARTProxy.jar <UART device> <xbee remote nodeID> <host> <localport> <admin port>
```
```sh
java -jar UARTProxyGateway.jar <UART device> <brokerhost> <remoteport>
```

both applications accept a **-v** parameter as the last argument, this will make the program very noisy printing almost every little piece of information (also exception messages of catched exceptions) so this is mainly if you are developing or debugging this application and not suitable for normal use.

On your device wich has the sensor (or which wants to publisch MQTT messages) and a XBee DigiMesh device attached on for example /dev/ttyS1 assuming that the Node identifier is set to **GATEWAY** on the receiving XBee device (using XCTU) you run **UARTProxy** like this, the admin port setting is there if you want to get access to the XBee device, as this device is opened by the proxy you cannot access the device anymore from the outside, so you can connect to the admin port using TCP/IP and issue XBee comamnds to interact with the device. Currently there is just a single command implemented which is **GETID** to retrieve the node id of the local device.

```sh
java -jar UARTProxy.jar /dev/ttyS1 GATEWAY localhost 1882 1881
```

in your MQTT applicatioin you then set the broker URL to tcp://localhost:1882

on your gateway which is attached to the network and able to reach the broker via TCP/IP you run the command
(assuming the XBee device is attached to /dev/ttyS1 and the broker host is 192.168.0.5 listening on default port 1883)

```sh
java -jar UARTProxyGateway.jar /dev/ttyS1 192.168.0.5 1883
```

## Remarks

It is recommended to sent a disconnect by the client after MQTT messages have been sent to give the remote peer an indication that the transfer has finished. Generally you should close your connection to the local UARTProxy after you've sent or received the data as we have a finite number of packet sequence numbers available. Sequence Numbers start at 0 when the connection is established and increment until the connection is closed or a disconnect is sent, so leaving the connection open and sending data again and again will sooner or later reach the limit of the maximum value of an integer.

I would recommend [Armbian](http://www.armbian.com) as they provide the rxtx package, you can install the rxtx library on armbian with 

```sh
apt-get intall librxtx-java
```
I used the [Rock Pi 4A](https://wiki.radxa.com/Rockpi4) as hardware platform, with XBee connected to UART4 /dev/ttyS4, which works very well.


## About

I'm the CEO of [P-i-U UG & Co. KG](http://www.p-i-u.de) and it happens that even I have to work sometimes ;-)
