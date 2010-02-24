/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package medopoker.network;

import java.io.IOException;
import java.util.Vector;
import javax.bluetooth.*;
import javax.microedition.io.*;

import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import medopoker.log.Log;

/**
 *
 * @author Nejc
 */
public class ClientCreator implements CommandListener, Runnable {
	private final Object inqLock = new Object();
	private final Object servLock = new Object();
	private final Object devLock = new Object();
	private InquiryListener inqListener;
	private ServiceListener servListener;
	private int devIndex = -1;
	private Device d;
	private Display disp;
	private ClientParent cp;

	public ClientCreator(MIDlet m) {
		disp = Display.getDisplay(m);
		cp = (ClientParent)m;
		connect();
	}

	private void connect() {
		Log.notify("Starting client...");

		Form form = new Form("Join game");
		disp.setCurrent(form);

		String msg = "Searching for devices...";
		form.append(msg);

		Thread t = new Thread(this);
		t.start();
	}

	public void run() {
		try {
			LocalDevice ld = LocalDevice.getLocalDevice();
			DiscoveryAgent da = ld.getDiscoveryAgent();
			ld.setDiscoverable(DiscoveryAgent.GIAC);

			Log.notify("Starting device inquiry...");
			inqListener = new InquiryListener();
			synchronized(inqLock) {
				da.startInquiry(DiscoveryAgent.GIAC, inqListener);
				try {inqLock.wait();} catch(InterruptedException e){}
			}

			if (inqListener.getDevicesFound().isEmpty()) {
				Log.err("No devices found!");
				System.exit(0);
			}

			displayDeviceList(inqListener.getDevicesFound());
			synchronized (devLock) {
				try {devLock.wait();} catch(InterruptedException e){}
			}
			RemoteDevice remoteDevice = (RemoteDevice) inqListener.getDevicesFound().elementAt(devIndex);

			servListener = new ServiceListener();
			UUID[] uuids = {new UUID("01101101", true)};
			synchronized(servLock) {
				da.searchServices(null, uuids, remoteDevice, servListener);
				try {servLock.wait();} catch(InterruptedException e){}
			}

			String url = servListener.getService().getConnectionURL(0, false);
			d = new Device((StreamConnection)Connector.open(url));
			
			cp.startClient(d);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void displayDeviceList(Vector devices) throws IOException {
		List list = new List("Devices found", List.IMPLICIT);
		for (int i=0; i<devices.size(); i++) {
			list.append(((RemoteDevice)devices.elementAt(i)).getFriendlyName(true), null);
		}
		list.setCommandListener(this);
		disp.setCurrent(list);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == List.SELECT_COMMAND) {
			devIndex = ((List)d).getSelectedIndex();
			synchronized (devLock) {
				devLock.notify();
			}
		}
	}

	private class InquiryListener implements DiscoveryListener {
		private Vector devsFound = new Vector();

		public Vector getDevicesFound() {
			return devsFound;
		}
		
		public void deviceDiscovered(RemoteDevice dev, DeviceClass ds) {
			Log.notify("Device discovered!");
			if (!devsFound.contains(dev))
				devsFound.addElement(dev);
		}

		public void inquiryCompleted(int arg0) {
			Log.notify("Inquiry completed.");
			synchronized(inqLock) {
				inqLock.notify();
			}
		}

		public void serviceSearchCompleted(int arg0, int arg1) {}
		public void servicesDiscovered(int arg0, ServiceRecord[] arg1) {}

	}

	private class ServiceListener implements DiscoveryListener {
		ServiceRecord service;

		public void deviceDiscovered(RemoteDevice arg0, DeviceClass arg1) {}
		public void inquiryCompleted(int arg0) {}

		public void serviceSearchCompleted(int arg0, int arg1) {
			synchronized(servLock) {servLock.notify();}
		}

		public void servicesDiscovered(int id, ServiceRecord[] sr) {
			service = sr[0];
		}

		public ServiceRecord getService() {
			return service;
		}

	}
}