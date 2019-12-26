package de.piu.examples;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import de.piu.config.Common;

public class Config extends Common implements Serializable {
	private static final long serialVersionUID = 1L;
	public String abasUser;
	public String abasHost;
	public String abasMandDir;
	public int abasEDPPort;
	public Map<String, Config.PrinterProperties> printers = new HashMap<String, Config.PrinterProperties>();
	private String[] printersArray;
	public Map<String, Integer> hosts = new HashMap<String, Integer>();
	public Map<String, Config.ClientHosts> clientHostMappings = new HashMap<String, Config.ClientHosts>();
	private String[] clientHostsArray;
	public int defaultPort;
	public int startChar;
	public int endChar;
	public int ackChar;
	public int nackChar;
	public char delimiter;
	public boolean checksum;
	public int clientCheckInterval;
	public int threadPools;
	protected boolean Debug = false;
	private String[] hostArray;
	public class PrinterProperties {
		public String type;
	}
	public class ClientHosts {
		public String host;
		public int port;
	}
	public static void main(String[] args) throws IOException {}
	
	public Config() {
		Properties conf = null;
		String ConfigFile = "ScaleControl.properties";
		try {
			conf = de.piu.config.Configuration.read(ConfigFile);
		} catch (IOException e) {
			e.printStackTrace();
		}		
		this.Debug = Boolean.parseBoolean(conf.getProperty("Debug"));
		this.abasUser = conf.getProperty("ABASUser");
		this.abasHost = conf.getProperty("ABASHost");
		this.abasMandDir = conf.getProperty("ABASMandDir");
		this.abasEDPPort = Integer.parseInt(conf.getProperty("ABASEDPPort"));
		this.printersArray = conf.getProperty("PRINTERS").split(",");
		this.defaultPort = Integer.parseInt(conf.getProperty("DEFAULT_PORT"));
		this.hostArray = conf.getProperty("HOSTS").split(",");
		this.clientHostsArray = conf.getProperty("CLIENT_HOST_MAPPINGS").split(",");
		
		for (int i = 0; i < printersArray.length; i++) {
			printersArray[i] = printersArray[i].trim();
			Config.PrinterProperties printerProperties = new Config.PrinterProperties();
			String printerName;
			if (printersArray[i].split(":").length == 2) {
				String temp[] = printersArray[i].split(":");
				printerName = temp[0];
				printerProperties.type = temp[1];
			} else {
				printerName = printersArray[i];
				printerProperties.type = "DEFAULT";
			}
		 printers.put(printerName, printerProperties);
		}
		for (int i = 0; i < hostArray.length; i++) {
			hostArray[i] = hostArray[i].trim();
			if (hostArray[i].split(":").length == 2) {
				String temp[] = hostArray[i].split(":");
				hosts.put(temp[0], Integer.parseInt(temp[1]));
			} else {
				hosts.put(hostArray[i], this.defaultPort);
			}
		}
		for (int i = 0; i < clientHostsArray.length; i++) {
			clientHostsArray[i] = clientHostsArray[i].trim();
			Config.ClientHosts clientHosts = new Config.ClientHosts();
			String clientHostname;
			if (clientHostsArray[i].split(":").length == 3) {
				String temp[] = clientHostsArray[i].split(":");
				clientHostname = temp[0];
				clientHosts.host = temp[1];
				clientHosts.port = Integer.parseInt(temp[2]);
			} else {
				String temp[] = clientHostsArray[i].split(":");
				clientHostname = temp[0];
				clientHosts.host = temp[1];
				clientHosts.port = this.defaultPort;
			}
		 clientHostMappings.put(clientHostname, clientHosts);
		}
		this.startChar = Integer.decode(conf.getProperty("STARTCHAR"));
		this.endChar = Integer.decode(conf.getProperty("ENDCHAR"));
		this.delimiter = conf.getProperty("DELIMITER").charAt(0);
		this.checksum = Boolean.parseBoolean(conf.getProperty("CHCKSUM"));
		this.ackChar = Integer.decode(conf.getProperty("ACKCHAR"));
		this.nackChar = Integer.decode(conf.getProperty("NACKCHAR"));
		this.clientCheckInterval = Integer.decode(conf.getProperty("CLIENT_CHECK_INTERVAL"));
		this.threadPools = Integer.decode(conf.getProperty("THREADPOOLS"));
		parseGroups(conf);
		if (Debug) {
			System.out.println("Using the following Configuration from " + ConfigFile + ":");
			System.out.println();
			System.out.println("ABASUser " + this.abasUser);
			System.out.println("ABASHost " + this.abasHost);
			System.out.println("ABASMandDir " + this.abasMandDir);
			System.out.println("ABASEDPPort " + this.abasEDPPort);
			System.out.println("PRINTERS ");
			Iterator<String> printer = printers.keySet().iterator();
			while(printer.hasNext()) {
				String key = printer.next();
				System.out.println("     Printer: " + key + " Type: " + printers.get(key).type);
			}
			System.out.println("HOSTS ");
			Iterator<String> hostaddress = hosts.keySet().iterator();
			while(hostaddress.hasNext()) {
				String key = hostaddress.next();
				System.out.println("     Host: " + key + " Port: " + hosts.get(key));
			}
			System.out.println("CLIENT_HOST_MAPPINGS ");
			Iterator<String> clienthostmapping = clientHostMappings.keySet().iterator();
			while(clienthostmapping.hasNext()) {
				String key = clienthostmapping.next();
				System.out.println("     Clienthost: " + key +  ", Scale Host (Destination): " + clientHostMappings.get(key).host + ", Port: " + clientHostMappings.get(key).port);
			}
			System.out.println("DEFAULT_PORT " + this.defaultPort);
			System.out.println("STARTCHAR " + this.startChar);
			System.out.println("ENDCHAR " + this.endChar);
			System.out.println("DELIMITER " + this.delimiter);
			System.out.println("CHKSUM " + this.checksum);
			System.out.println("ACKCHAR " + this.ackChar);
			System.out.println("NACKCHAR " + this.nackChar);
			System.out.println("CLIENT_CHECK_INTERVAL " + this.clientCheckInterval);
			System.out.println("THREADPOOLS " + this.threadPools);
		}
	} 
}
