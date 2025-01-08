/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  VehicularScenarioFactory provides the default
 *               instances of required abstract classes 
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app5;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.core.ScenarioFactoryEnergy;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.energy.DefaultEnergyComputingModel;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;

public class VehicularScenarioFactory implements ScenarioFactoryEnergy {
	private int numOfMobileDevice;
	private double simulationTime;
	private String orchestratorPolicy;
	private String simScenario;
	private double maxActiveConsumption;
	private double idleConsumption;

	VehicularScenarioFactory(int _numOfMobileDevice, double _simulationTime, String _orchestratorPolicy,
			String _simScenario, double _maxActiveConsumption, double _idleConsumption) {
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
		this.idleConsumption = _idleConsumption;
		this.maxActiveConsumption = _maxActiveConsumption;
	}

	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		return new VehicularLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		return new VehicularEdgeOrchestrator(numOfMobileDevice, orchestratorPolicy, simScenario);
	}

	@Override
	public MobilityModel getMobilityModel() {
		return new VehicularMobilityModel(numOfMobileDevice, simulationTime);
//		return new NomadicMobility(numOfMobileDevice, simulationTime);
	}

	@Override
	public NetworkModel getNetworkModel() {
		return new VehicularNetworkModel(numOfMobileDevice, simScenario, orchestratorPolicy);
	}

	@Override
	public EdgeServerManager getEdgeServerManager() {
		return new VehicularEdgeServerManager();
	}

	@Override
	public CloudServerManager getCloudServerManager() {
		return new DefaultCloudServerManager();
	}

	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		return new VehicularMobileDeviceManager();
	}

	@Override
	public MobileServerManager getMobileServerManager() {
		return new VehicularMobileServerManager(numOfMobileDevice);
	}

	@Override
	public String getEnergyModel() {
		double result = getDefaultEnergyComputerModel().getTotalEnergyConsumption();
		String resultMsg = String.format("For idleConsumption %.2f and maxActiveConsumption %.2f, il consumo è %.2f",
				idleConsumption, maxActiveConsumption, result);
		return resultMsg;
	}

	@Override
	public DefaultEnergyComputingModel getDefaultEnergyComputerModel() {
		DefaultEnergyComputingModel defaultEnergyComputingModel = new DefaultEnergyComputingModel(numOfMobileDevice,
				maxActiveConsumption, idleConsumption);
		return defaultEnergyComputingModel;
	}
}
