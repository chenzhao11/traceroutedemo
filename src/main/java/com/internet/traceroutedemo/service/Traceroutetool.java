/**
 * @ClassName Traceroutetool.java
 * @author zhao
 * @version 1.0.0
 * @Description TODO
 * @createTime 2020年12月25日 22:23:00
 */
package com.internet.traceroutedemo.service;

import jpcap.JpcapCaptor;
import jpcap.NetworkInterface;
import jpcap.NetworkInterfaceAddress;

import java.util.Scanner;

public class Traceroutetool {


    public static void print(String text) {
        System.out.println(text);
    }

    public static void printDeviceInfo() {
        NetworkInterface[] devlist = JpcapCaptor.getDeviceList();
        String[] deviceinfo = new String[devlist.length];
        for (int i = 0; i < devlist.length; i++) {
            System.out.println("device" + i + ":   " + devlist[i].name);
            System.out.println(devlist[i].description);
            NetworkInterfaceAddress[] addresses = devlist[i].addresses;
            for (NetworkInterfaceAddress j : addresses) {
                System.out.println(j.address);
            }
            print("===================================================");
        }

    }

    public static void main(String[] args) {
        Traceroute traceroute = new Traceroute();
        Scanner input = new Scanner(System.in);
        printDeviceInfo();
        print("请选择您要使用的设备：");
        int deviceno = input.nextInt();
        while (true) {
            print("请输入您想要trace的地址：");
            String traceaddress = input.next();
            traceroute.trace(deviceno, traceaddress);
        }
    }
}
