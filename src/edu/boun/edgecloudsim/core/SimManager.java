/*
 * Title:        EdgeCloudSim - Simulation Manager
 * 
 * Description: 
 * SimManager is an singleton class providing many abstract classeses such as
 * Network Model, Mobility Model, Edge Orchestrator to other modules
 * Critical simulation related information would be gathered via this class 
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeHostEnergy;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeVmAllocationPolicy_Custom;
import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileHostEnergy;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.TaskProperty;
import edu.boun.edgecloudsim.utils.Coordinates;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class SimManager extends SimEntity {
	private static final int CREATE_TASK = 0;
	private static final int CHECK_ALL_VM = 1;
	private static final int GET_LOAD_LOG = 2;
	private static final int PRINT_PROGRESS = 3;
	private static final int STOP_SIMULATION = 4;

	private String simScenario;
	private String orchestratorPolicy;
	private int numOfMobileDevice;
	private NetworkModel networkModel;
	private MobilityModel mobilityModel;
	private ScenarioFactory scenarioFactory;
	private EdgeOrchestrator edgeOrchestrator;
	private EdgeServerManager edgeServerManager;
	private CloudServerManager cloudServerManager;
	private MobileServerManager mobileServerManager;
	private LoadGeneratorModel loadGeneratorModel;
	private MobileDeviceManager mobileDeviceManager;

	private static SimManager instance = null;

	List<Coordinates> coordinatesList = null;

	public SimManager(ScenarioFactory _scenarioFactory, int _numOfMobileDevice, String _simScenario,
			String _orchestratorPolicy) throws Exception {
		super("SimManager");
		simScenario = _simScenario;
		scenarioFactory = _scenarioFactory;
		numOfMobileDevice = _numOfMobileDevice;
		orchestratorPolicy = _orchestratorPolicy;

		SimLogger.print("Creating tasks...");
		loadGeneratorModel = scenarioFactory.getLoadGeneratorModel();
		loadGeneratorModel.initializeModel();
		SimLogger.printLine("Done, ");

		SimLogger.print("Creating device locations...");
		mobilityModel = scenarioFactory.getMobilityModel();
		mobilityModel.initialize();
		SimLogger.printLine("Done.");

		// Generate network model
		SimLogger.printLine("Initializing network model...");
		networkModel = scenarioFactory.getNetworkModel();
		networkModel.initialize();

		// Generate edge orchestrator
		edgeOrchestrator = scenarioFactory.getEdgeOrchestrator();
		edgeOrchestrator.initialize();

		// Create Physical Servers
		edgeServerManager = scenarioFactory.getEdgeServerManager();
		edgeServerManager.initialize();

		// Create Physical Servers on cloud
		cloudServerManager = scenarioFactory.getCloudServerManager();
		cloudServerManager.initialize();

		// Create Physical Servers on mobile devices
		mobileServerManager = scenarioFactory.getMobileServerManager();
		mobileServerManager.initialize();

		// Create Client Manager
		mobileDeviceManager = scenarioFactory.getMobileDeviceManager();
		mobileDeviceManager.initialize();

		instance = this;
	}

	public static SimManager getInstance() {
		return instance;
	}

	/**
	 * Triggering CloudSim to start simulation
	 */
	public void startSimulation() throws Exception {
		// Starts the simulation
		SimLogger.print(super.getName() + " is starting...");

		// Start Edge Datacenters & Generate VMs
		edgeServerManager.startDatacenters();
		edgeServerManager.startDatacentersEnegy();
		edgeServerManager.createVmList(mobileDeviceManager.getId());

		// Start Edge Datacenters & Generate VMs
		cloudServerManager.startDatacenters();
		cloudServerManager.createVmList(mobileDeviceManager.getId());

		// Start Mobile Datacenters & Generate VMs
		mobileServerManager.startDatacenters();
		mobileServerManager.createVmList(mobileDeviceManager.getId());

//		((VehicularNetworkModel) networkModel)
//		.setEdgeServerManager((VehicularEdgeServerManager) scenarioFactory.getEdgeServerManager());

		CloudSim.startSimulation();
	}

	public String getSimulationScenario() {
		return simScenario;
	}

	public String getOrchestratorPolicy() {
		return orchestratorPolicy;
	}

	public ScenarioFactory getScenarioFactory() {
		return scenarioFactory;
	}

	public int getNumOfMobileDevice() {
		return numOfMobileDevice;
	}

	public NetworkModel getNetworkModel() {
		return networkModel;
	}

	public MobilityModel getMobilityModel() {
		return mobilityModel;
	}

	public EdgeOrchestrator getEdgeOrchestrator() {
		return edgeOrchestrator;
	}

	public EdgeServerManager getEdgeServerManager() {
		return edgeServerManager;
	}

	public CloudServerManager getCloudServerManager() {
		return cloudServerManager;
	}

	public MobileServerManager getMobileServerManager() {
		return mobileServerManager;
	}

	public LoadGeneratorModel getLoadGeneratorModel() {
		return loadGeneratorModel;
	}

	public MobileDeviceManager getMobileDeviceManager() {
		return mobileDeviceManager;
	}

	@Override
	public void startEntity() {
		int hostCounter = 0;

		for (int i = 0; i < edgeServerManager.getDatacenterList().size(); i++) {
			List<? extends Host> list = edgeServerManager.getDatacenterList().get(i).getHostList();
			for (int j = 0; j < list.size(); j++) {
				mobileDeviceManager.submitVmList(edgeServerManager.getVmList(hostCounter));
				hostCounter++;
			}
		}

		for (int i = 0; i < SimSettings.getInstance().getNumOfCloudHost(); i++) {
			mobileDeviceManager.submitVmList(cloudServerManager.getVmList(i));
		}

		for (int i = 0; i < numOfMobileDevice; i++) {
			if (mobileServerManager.getVmList(i) != null)
				mobileDeviceManager.submitVmList(mobileServerManager.getVmList(i));
		}

		// Creation of tasks are scheduled here!
		for (int i = 0; i < loadGeneratorModel.getTaskList().size(); i++)
			schedule(getId(), loadGeneratorModel.getTaskList().get(i).getStartTime(), CREATE_TASK,
					loadGeneratorModel.getTaskList().get(i));

		// Periodic event loops starts from here!
		schedule(getId(), 5, CHECK_ALL_VM);
		schedule(getId(), SimSettings.getInstance().getSimulationTime() / 100, PRINT_PROGRESS);
		schedule(getId(), SimSettings.getInstance().getVmLoadLogInterval(), GET_LOAD_LOG);
		schedule(getId(), SimSettings.getInstance().getSimulationTime(), STOP_SIMULATION);

		SimLogger.printLine("Done.");
	}

	@Override
	public void processEvent(SimEvent ev) {
		synchronized (this) {
			switch (ev.getTag()) {
			case CREATE_TASK:
				try {
					TaskProperty edgeTask = (TaskProperty) ev.getData();
					Task task = mobileDeviceManager.submitTask(edgeTask);
					if (task == null)
						return; // task is blocked because mobileHost is dead, no other action is needed here
					calculateNetConsume(task, SimUtils.TRANSMISSION);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(1);
				}
				break;
			case CHECK_ALL_VM:
				int totalNumOfVm = SimSettings.getInstance().getNumOfEdgeVMs();
				if (EdgeVmAllocationPolicy_Custom.getCreatedVmNum() != totalNumOfVm) {
					SimLogger.printLine("All VMs cannot be created! Terminating simulation...");
					System.exit(1);
				}
				break;
			case GET_LOAD_LOG:
				SimLogger.getInstance().addVmUtilizationLog(CloudSim.clock(), edgeServerManager.getAvgUtilization(),
						cloudServerManager.getAvgUtilization(), mobileServerManager.getAvgUtilization());

				schedule(getId(), SimSettings.getInstance().getVmLoadLogInterval(), GET_LOAD_LOG);
				break;
			case PRINT_PROGRESS:
				int progress = (int) ((CloudSim.clock() * 100) / SimSettings.getInstance().getSimulationTime());
				if (progress % 10 == 0)
					SimLogger.print(Integer.toString(progress));
				else
					SimLogger.print(".");
				if (CloudSim.clock() < SimSettings.getInstance().getSimulationTime())
					schedule(getId(), SimSettings.getInstance().getSimulationTime() / 100, PRINT_PROGRESS);

				break;
			case STOP_SIMULATION:
				SimLogger.printLine("100");
				CloudSim.terminateSimulation();
				try {
					SimLogger.getInstance().simStopped();
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				break;
			default:
				SimLogger.printLine(getName() + ": unknown event type");
				break;
			}
		}
	}

	@Override
	public void shutdownEntity() {
		edgeServerManager.terminateDatacenters();
		cloudServerManager.terminateDatacenters();
		mobileServerManager.terminateDatacenters();
	}

	public void calculateNetConsume(Task task, int flag) {
		long size;
		if (flag == SimUtils.TRANSMISSION) {
//    		System.out.println("UPLOAD");
			size = task.getCloudletFileSize();
		} else {
//    		System.out.println("DOWNLOAD");
			size = task.getCloudletOutputSize();
		}

		int nexthop = getEdgeOrchestrator().getDeviceToOffload(task);

		int mobileid = task.getMobileDeviceId();
		Location loc_mobile = getMobilityModel().getLocation(mobileid, CloudSim.clock());

		MobileHostEnergy host = ((MobileHostEnergy) getMobileServerManager().getDatacenter().getHostList()
				.get(mobileid));

		// for diagrams constructions
		double energyConsumed = host.energyConsumption(CloudSim.clock());

		if (coordinatesList == null) {
			coordinatesList = new ArrayList<>();
		}
		coordinatesList.add(new Coordinates(loc_mobile.getXPos(), loc_mobile.getYPos(), host.isDead(), mobileid,
				CloudSim.clock(), energyConsumed));

		EdgeServerManager esm = getEdgeServerManager();

		// GSM mobile to cloud OK
		// WLAN mobile to edge OK

		// MAN edge to edge ? TODO
		// WAN edge to cloud ? TODO

		switch (nexthop) {
		case SimSettings.CLOUD_DATACENTER_ID: {
			host.getEnergyModel().setConnectivityType(NETWORK_DELAY_TYPES.GSM_DELAY);
//			System.out.println("-------TO---CLOUD");
			break;
		}
		case SimSettings.MOBILE_DATACENTER_ID: { // DEVICE TO DEVICE ?
//			System.out.println("-------TO---MOBILE");
			host.getEnergyModel().setConnectivityType(NETWORK_DELAY_TYPES.GSM_DELAY);
			break;
		}
		case SimSettings.GENERIC_EDGE_DEVICE_ID: {
//			System.out.println("-------TO---EDGE");
			host.getEnergyModel().setConnectivityType(NETWORK_DELAY_TYPES.WLAN_DELAY);
			for (Datacenter d : esm.getDatacenterList()) {
				EdgeHostEnergy edgehost = (EdgeHostEnergy) d.getHostList().get(0); // one host per datacenter
				if (edgehost.getLocation().equals(loc_mobile)) {
//					System.out.println("--------------"+locm.getXPos()+","+locm.getYPos());
					edgehost.getEnergyModel().setConnectivityType(NETWORK_DELAY_TYPES.WLAN_DELAY);
					edgehost.getEnergyModel().updatewirelessEnergyConsumption(size, flag);
				}
			}
			break;
		}
		default:// TODO
			host.getEnergyModel().setConnectivityType(NETWORK_DELAY_TYPES.WLAN_DELAY);
			System.err.println("-------TO---????" + nexthop);
			break;

		}
		host.getEnergyModel().updatewirelessEnergyConsumption(size, flag);

	}

}
