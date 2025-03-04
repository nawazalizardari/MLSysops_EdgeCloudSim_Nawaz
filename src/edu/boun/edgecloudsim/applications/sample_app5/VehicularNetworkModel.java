/*
 * Title:        EdgeCloudSim - Network model implementation
 * 
 * Description: 
 * VehicularNetworkModel implements MMPP/M/1 queue model for
 * WLAN, MAN, WAN and GSM based communication
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.sample_app5;

import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.core.SimSettings.NETWORK_DELAY_TYPES;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

public class VehicularNetworkModel extends NetworkModel {

//	private static final double FREQUENCY_5G = 3.5 * 1000; // GHz
	private static final double PATH_LOSS_EXPONENT = 3.1;
	private static final double SHADOW_FADING_STD_DEV = 8.1; // dB
	private static final double SPEED_OF_LIGHT = 3e8; // m/s
	private static final double EARTH_RADIUS = 6371000; // Earth radius in meters
	
	private static final double REFERENCE_DISTANCE = 1.0; // meters
	private static final double CARRIER_FREQUENCY = 5.9e9; // 5.9 GHz (V2X band)
	private static final double VEHICULAR_PATH_LOSS_EXPONENT = 2.5; // Vehicular path loss exponent
	private static final double SHADOWING_STD_DEV = 8.0; // dB (vehicular environment)
	private static final double NOISE_FIGURE = 5; // dB (receiver noise figure)
	private static final double NOISE_DENSITY = -174; // dBm/Hz (thermal noise)
	private static final double ANTENNA_GAIN = 20; // dBi (antenna gain for beamforming)
	private static final double TX_POWER = 30; // dBm (transmit power)
	private static final int NUM_ANTENNAS = 4; // Number of antennas for MIMO
	

	public static double maxWlanDelay = 0;
	public static double maxWanDelay = 0;
//	public static double maxGsmDelay = 0;
	public static double max5GDelay = 0;
	private Task task;

	private class MMPPWrapper {
		private double currentPoissonMean;
		private double currentTaskSize;

		// record last values used for successful packet transmission
		private double lastPoissonMean;
		private double lastTaskSize;

		// record last n task statistics during MM1_QUEUE_MODEL_UPDATE_INTEVAL seconds
		// to simulate mmpp/m/1 queue model
		private double numOfTasks;
		private double totalTaskSize;

		public MMPPWrapper() {
			currentPoissonMean = 0;
			currentTaskSize = 0;

			lastPoissonMean = 0;
			lastTaskSize = 0;

			numOfTasks = 0;
			totalTaskSize = 0;
		}

		public double getPoissonMean() {
			return currentPoissonMean;
		}

		public double getTaskSize() {
			return currentTaskSize;
		}

		public void increaseMM1StatValues(double taskSize) {
			numOfTasks++;
			totalTaskSize += taskSize;
		}

		public void initializeMM1QueueValues(double poissonMean, double taskSize, double bandwidth) {
			currentPoissonMean = poissonMean;
			currentTaskSize = taskSize;

			lastPoissonMean = poissonMean;
			lastTaskSize = taskSize;

			double avgTaskSize = taskSize * 8; // convert from KB to Kb
			double lamda = ((double) 1 / (double) poissonMean); // task per seconds
			double mu = bandwidth /* Kbps */ / avgTaskSize /* Kb */; // task per seconds

			/*
			 * 
			 * SimLogger.printLine("Initializing MM1 Queue Values:");
			 * SimLogger.printLine("Bandwidth: " + bandwidth + " Kbps");
			 * SimLogger.printLine("Task Size: " + avgTaskSize + " Kb");
			 * SimLogger.printLine("Poisson Mean: " + poissonMean);
			 * SimLogger.printLine("Lambda (arrival rate): " + lamda);
			 * SimLogger.printLine("Mu (service rate): " + mu + "\n==========");
			 */

			if (mu <= lamda) {
				SimLogger.printLine("Error in initializeMM1QueueValues function:"
						+ "MU is smallar than LAMDA! Check your simulation settings.");
				System.exit(1);
			}
		}

		public void updateLastSuccessfulMM1QueueValues() {
			lastPoissonMean = currentPoissonMean;
			lastTaskSize = currentTaskSize;
		}

		public void updateMM1Values(double interval, double optionalBackgroundDataCount,
				double optionalBackgroundDataSize) {
			if (numOfTasks == 0) {
				currentPoissonMean = lastPoissonMean;
				currentTaskSize = lastTaskSize;
			} else {
				double poissonMean = interval / (numOfTasks + optionalBackgroundDataCount);
				double taskSize = (totalTaskSize + optionalBackgroundDataSize)
						/ (numOfTasks + optionalBackgroundDataCount);

				if (CloudSim.clock() > SimSettings.getInstance().getWarmUpPeriod() && poissonMean > currentPoissonMean)
					poissonMean = (poissonMean + currentPoissonMean * 3) / 4;

				currentPoissonMean = poissonMean;
				currentTaskSize = taskSize;
			}

			numOfTasks = 0;
			totalTaskSize = 0;
		}
	}

	private static double MAN_CONTROL_MESSAGE_PER_SECONDS = 10;
	private static double MAN_CONTROL_MESSAGE_SIZE = 25; // 100 KB

	private double lastMM1QueeuUpdateTime;

	private MMPPWrapper[] wlanMMPPForDownload;
	private MMPPWrapper[] wlanMMPPForUpload;

	private MMPPWrapper manMMPPForDownload;
	private MMPPWrapper manMMPPForUpload;

	private MMPPWrapper wanMMPPForDownload;
	private MMPPWrapper wanMMPPForUpload;

	// private MMPPWrapper gsmMMPPForDownload;
	// private MMPPWrapper gsmMMPPForUpload;

	private MMPPWrapper fiveGMMPPForDownload;
	private MMPPWrapper fiveGMMPPForUpload;

//	private VehicularEdgeServerManager edgeServerManager;

	public VehicularNetworkModel(int _numberOfMobileDevices, String _simScenario, String _orchestratorPolicy) {
		super(_numberOfMobileDevices, _simScenario);
		lastMM1QueeuUpdateTime = SimSettings.CLIENT_ACTIVITY_START_TIME;
	}

	@Override
	public void initialize() {
		SimSettings SS = SimSettings.getInstance();
		this.task = null;

		int numOfApp = SimSettings.getInstance().getTaskLookUpTable().length;
		int numOfAccessPoint = SimSettings.getInstance().getNumOfEdgeDatacenters();

		wlanMMPPForDownload = new MMPPWrapper[numOfAccessPoint];
		wlanMMPPForUpload = new MMPPWrapper[numOfAccessPoint];
		for (int apIndex = 0; apIndex < numOfAccessPoint; apIndex++) {
			wlanMMPPForDownload[apIndex] = new MMPPWrapper();
			wlanMMPPForUpload[apIndex] = new MMPPWrapper();
		}

		manMMPPForDownload = new MMPPWrapper();
		manMMPPForUpload = new MMPPWrapper();

		wanMMPPForDownload = new MMPPWrapper();
		wanMMPPForUpload = new MMPPWrapper();

		// gsmMMPPForDownload = new MMPPWrapper();
		// gsmMMPPForUpload = new MMPPWrapper();
		fiveGMMPPForDownload = new MMPPWrapper();
		fiveGMMPPForUpload = new MMPPWrapper();

		// Approximate usage of the access technologies for the first MMPP time slot
		double probOfWlanComm = 0.40;
		double probOfWanComm = 0.15;
		// double probOfGsmComm = 0.10;
		double probOf5GComm = 0.10;
		// double probOfManComm = 0.35;
		double probOfManComm = 0.35;

		double weightedTaskPerSecond = 0;
		double weightedTaskInputSize = 0;
		double weightedTaskOutputSize = 0;

		// Calculate interarrival time and task sizes
		for (int taskIndex = 0; taskIndex < numOfApp; taskIndex++) {
			double percentageOfAppUsage = SS.getTaskLookUpTable()[taskIndex][0];
			double poissonOfApp = SS.getTaskLookUpTable()[taskIndex][2];
			double taskInputSize = SS.getTaskLookUpTable()[taskIndex][5];
			double taskOutputSize = SS.getTaskLookUpTable()[taskIndex][6];

			if (percentageOfAppUsage <= 0 && percentageOfAppUsage > 100) {
				SimLogger.printLine("Usage percantage of task " + taskIndex + " is invalid (" + percentageOfAppUsage
						+ ")! Terminating simulation...");
				System.exit(1);
			}

			weightedTaskInputSize += taskInputSize * (percentageOfAppUsage / (double) 100);
			weightedTaskOutputSize += taskOutputSize * (percentageOfAppUsage / (double) 100);

			weightedTaskPerSecond += ((double) 1 / poissonOfApp) * (percentageOfAppUsage / (double) 100);
		}

		for (int apIndex = 0; apIndex < numOfAccessPoint; apIndex++) {
			double poisson = (double) 1
					/ (weightedTaskPerSecond * (numberOfMobileDevices / numOfAccessPoint) * probOfWlanComm);
			wlanMMPPForDownload[apIndex].initializeMM1QueueValues(poisson, weightedTaskOutputSize,
					SimSettings.getInstance().getWlanBandwidth());
			wlanMMPPForUpload[apIndex].initializeMM1QueueValues(poisson, weightedTaskInputSize,
					SimSettings.getInstance().getWlanBandwidth());
		}

		double poisson = (double) 1 / (weightedTaskPerSecond * numberOfMobileDevices * probOfManComm);
		manMMPPForDownload.initializeMM1QueueValues(poisson, weightedTaskOutputSize,
				SimSettings.getInstance().getManBandwidth());
		manMMPPForUpload.initializeMM1QueueValues(poisson, weightedTaskInputSize,
				SimSettings.getInstance().getManBandwidth());

		poisson = (double) 1 / (weightedTaskPerSecond * numberOfMobileDevices * probOfWanComm);
		wanMMPPForDownload.initializeMM1QueueValues(poisson, weightedTaskOutputSize,
				SimSettings.getInstance().getWanBandwidth());
		wanMMPPForUpload.initializeMM1QueueValues(poisson, weightedTaskInputSize,
				SimSettings.getInstance().getWanBandwidth());

		// poisson = (double)1 / (weightedTaskPerSecond * numberOfMobileDevices *
		// probOfGsmComm);
		// gsmMMPPForDownload.initializeMM1QueueValues(poisson, weightedTaskOutputSize,
		// SimSettings.getInstance().getGsmBandwidth());
		// gsmMMPPForUpload.initializeMM1QueueValues(poisson, weightedTaskInputSize,
		// SimSettings.getInstance().getGsmBandwidth());

		poisson = (double) 1 / (weightedTaskPerSecond * numberOfMobileDevices * probOf5GComm);

		// double adjustedBandwidth =
		// adjustBandwidth(SimSettings.getInstance().get5GBandwidth());

		fiveGMMPPForDownload.initializeMM1QueueValues(poisson, weightedTaskOutputSize,
				SimSettings.getInstance().get5GBandwidth());
		// FREQUENCY_5G);

		fiveGMMPPForUpload.initializeMM1QueueValues(poisson, weightedTaskInputSize,
				SimSettings.getInstance().get5GBandwidth());
		// FREQUENCY_5G);

	}

	/*
	 * private double adjustBandwidth(double bandwidth) { if (bandwidth <
	 * MIN_EFFECTIVE_BANDWIDTH) { SimLogger.
	 * printLine("Effective bandwidth is too low. Adjusting to minimum threshold.");
	 * return MIN_EFFECTIVE_BANDWIDTH; } return bandwidth; }
	 */

	/**
	 * source device is always mobile device in our simulation scenarios!
	 */
	@Override
	public double getUploadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		SimLogger.printLine("getUploadDelay is not used in this scenario! Terminating simulation...");
		System.exit(1);
		return 0;
	}

	/**
	 * destination device is always mobile device in our simulation scenarios!
	 */
	@Override
	public double getDownloadDelay(int sourceDeviceId, int destDeviceId, Task task) {
		SimLogger.printLine("getDownloadDelay is not used in this scenario! Terminating simulation...");
		System.exit(1);
		return 0;
	}

	@Override
	public void uploadStarted(Location accessPointLocation, int destDeviceId) {
		SimLogger.printLine("uploadStarted is not used in this scenario! Terminating simulation...");
		System.exit(1);
	}

	@Override
	public void uploadFinished(Location accessPointLocation, int destDeviceId) {
		SimLogger.printLine("uploadFinished is not used in this scenario! Terminating simulation...");
		System.exit(1);
	}

	@Override
	public void downloadStarted(Location accessPointLocation, int sourceDeviceId) {
		SimLogger.printLine("downloadStarted is not used in this scenario! Terminating simulation...");
		System.exit(1);
	}

	@Override
	public void downloadFinished(Location accessPointLocation, int sourceDeviceId) {
		SimLogger.printLine("downloadFinished is not used in this scenario! Terminating simulation...");
		System.exit(1);
	}

	public double estimateWlanDownloadDelay(int apId) {
		return getWlanDownloadDelay(0, apId, true);
	}

	public double estimateWlanUploadDelay(int apId) {
		return getWlanUploadDelay(0, apId, true);
	}

	public double estimateUploadDelay(NETWORK_DELAY_TYPES delayType, Task task) {
		return getDelay(delayType, task, false, true);
	}

	public double estimateDownloadDelay(NETWORK_DELAY_TYPES delayType, Task task) {
		return getDelay(delayType, task, true, true);
	}

	public double getUploadDelay(NETWORK_DELAY_TYPES delayType, Task task) {
		return getDelay(delayType, task, false, false);
	}

	public double getDownloadDelay(NETWORK_DELAY_TYPES delayType, Task task) {
		return getDelay(delayType, task, true, false);
	}

	private double getDelay(NETWORK_DELAY_TYPES delayType, Task task, boolean forDownload, boolean justEstimate) {
		double delay = 0;
		double time = CloudSim.clock();

//		if(delayType == NETWORK_DELAY_TYPES.GSM_DELAY){
//			if(forDownload)
//				delay = getGsmDownloadDelay(task.getCloudletOutputSize(), justEstimate);
//			else
//				delay = getGsmUploadDelay(task.getCloudletFileSize(), justEstimate);
//
//			if(delay != 0)
//				delay += SimSettings.getInstance().getGsmPropagationDelay();
//		}

		if (delayType == NETWORK_DELAY_TYPES.WLAN_DELAY) {
			if (forDownload)
				delay = getWlanDownloadDelay(task.getCloudletOutputSize(),
						task.getSubmittedLocation().getServingWlanId(), justEstimate);
			else
				delay = getWlanUploadDelay(task.getCloudletFileSize(), task.getSubmittedLocation().getServingWlanId(),
						justEstimate);
		} else if (delayType == NETWORK_DELAY_TYPES.WAN_DELAY) {
			if (forDownload)
				delay = getWanDownloadDelay(task.getCloudletOutputSize(), justEstimate);
			else
				delay = getWanUploadDelay(task.getCloudletFileSize(), justEstimate);

			if (delay != 0)
				delay += SimSettings.getInstance().getWanPropagationDelay();
		} else if (delayType == NETWORK_DELAY_TYPES.MAN_DELAY) {
			if (forDownload)
				delay = getManDownloadDelay(task.getCloudletOutputSize(), justEstimate);
			else
				delay = getManUploadDelay(task.getCloudletFileSize(), justEstimate);

			if (delay != 0)
				delay += SimSettings.getInstance().getInternalLanDelay();
		} else if (delayType == NETWORK_DELAY_TYPES.FIVEG_DELAY) {

			this.task = task;

			Location sourceLoc = task.getSubmittedLocation();
			Location desLoca = getDestinationLocByTask(task, sourceLoc);
			//int dId = task.getMobileDeviceId();
//			boolean log = dId == 10;
			boolean log = true;

			if (forDownload) {
				delay = get5GDownloadDelay(task.getCloudletOutputSize(), log, time, sourceLoc, desLoca, justEstimate);
			} else {
				delay = get5GUploadDelay(task.getCloudletFileSize(), false, time, sourceLoc, desLoca, justEstimate);
			}

			/*
			if (delay != 0) {
				delay += calculate5GPropagationDelay(sourceLoc, desLoca);
			}
			*/
		}
		
		SimLogger.getInstance().PrintDelayLog(time, delay);

		return delay;
	}

	private double calculate5GPropagationDelay(Location sourceLocation, Location destLocation) {
		double distance = Haversine.calculateDistance(sourceLocation.getLatitude(), sourceLocation.getLongitude(),
				destLocation.getLatitude(), destLocation.getLongitude());
		return distance / SPEED_OF_LIGHT;
	}

	private Location getDestinationLocByTask(Task task, Location sourceLoc) {

		FCDReader fcd = FCDReader.getInstance();
		List<DataCenter> dc = fcd.getDataCenters();

//		Location sourceLoc = task.getSubmittedLocation();
		Vehicle v = new Vehicle(task.getMobileDeviceId(), sourceLoc.getXPos(), sourceLoc.getYPos(), -1);

		int hostIndex = MyUtilities.getNearestDataCenterByPower(fcd.getDataCenters(), v);
		DataCenter d = dc.get(hostIndex);
		Location desLoca = d.getLocation();

//		org.cloudbus.cloudsim.Datacenter d=	getEdgeServerManager().getDatacenterList().get(hostIndex);
//		d
//		
//		Location sourceLoc = task.getSubmittedLocation();
//		int wLanId = sourceLoc.getServingWlanId();
//		Location desLoca = null;
//		for (Datacenter d : getEdgeServerManager().getDatacenterList()) {
//			for (Host host : d.getHostList()) {
//				EdgeHost h = (EdgeHost) host;
//				if (h.getLocation().getServingWlanId() == wLanId)
//					desLoca = h.getLocation();
//				break;
//			}
//		}
		return desLoca;
	}

	private double calculateMM1(double taskSize, double bandwidth /* Kbps */, MMPPWrapper mmppWrapper,
			boolean justEstimate) {
		double mu = 0, lamda = 0;
		double PoissonMean = mmppWrapper.getPoissonMean();
		double avgTaskSize = mmppWrapper.getTaskSize(); /* KB */

		if (!justEstimate)
			mmppWrapper.increaseMM1StatValues(taskSize);

		avgTaskSize = avgTaskSize * 8; // convert from KB to Kb

		lamda = ((double) 1 / (double) PoissonMean); // task per seconds
		mu = bandwidth /* Kbps */ / avgTaskSize /* Kb */; // task per seconds
		double result = (double) 1 / (mu - lamda);

		return (result > 7.5 || result < 0) ? 0 : result;
	}

	private double getWlanDownloadDelay(double taskSize, int accessPointId, boolean justEstimate) {
		double bw = SimSettings.getInstance().getWlanBandwidth();

		double result = calculateMM1(taskSize, bw, wlanMMPPForDownload[accessPointId], justEstimate);

		if (maxWlanDelay < result)
			maxWlanDelay = result;

		return result;
	}

	private double getWlanUploadDelay(double taskSize, int accessPointId, boolean justEstimate) {
		double bw = SimSettings.getInstance().getWlanBandwidth();

		double result = calculateMM1(taskSize, bw, wlanMMPPForUpload[accessPointId], justEstimate);

		if (maxWlanDelay < result)
			maxWlanDelay = result;

		return result;
	}

	private double getManDownloadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getManBandwidth();

		double result = calculateMM1(taskSize, bw, manMMPPForDownload, justEstimate);

		return result;
	}

	private double getManUploadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getManBandwidth();

		double result = calculateMM1(taskSize, bw, manMMPPForUpload, justEstimate);

		return result;
	}

	private double getWanDownloadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getWanBandwidth();

		double result = calculateMM1(taskSize, bw, wanMMPPForDownload, justEstimate);

		if (maxWanDelay < result)
			maxWanDelay = result;

		return result;
	}

	private double getWanUploadDelay(double taskSize, boolean justEstimate) {
		double bw = SimSettings.getInstance().getWanBandwidth();

		double result = calculateMM1(taskSize, bw, wanMMPPForUpload, justEstimate);

		if (maxWanDelay < result)
			maxWanDelay = result;

		return result;
	}

	/*
	 * private double getGsmDownloadDelay(double taskSize, boolean justEstimate) {
	 * double bw = SimSettings.getInstance().getGsmBandwidth();
	 * 
	 * double result = calculateMM1(taskSize, bw, gsmMMPPForDownload, justEstimate);
	 * 
	 * if (maxGsmDelay < result) maxGsmDelay = result;
	 * 
	 * return result; }
	 * 
	 * private double getGsmUploadDelay(double taskSize, boolean justEstimate) {
	 * double bw = SimSettings.getInstance().getGsmBandwidth();
	 * 
	 * double result = calculateMM1(taskSize, bw, gsmMMPPForUpload, justEstimate);
	 * 
	 * if (maxGsmDelay < result) maxGsmDelay = result;
	 * 
	 * return result; }
	 */

//	private double get5GDownloadDelay(double taskSize, Task task, boolean justEstimate) {
////	return	get5GDownloadDelay(taskSize)
//	}

	private double get5GDownloadDelay(double taskSize, boolean log, double time, Location sourceLocation,
			Location destLocation, boolean justEstimate) {

				double taskSizeInBits = taskSize * 1000 * 8;
		
				double bw = SimSettings.getInstance().get5GBandwidth();
				
			//	double distance = calculateDistance(sourceLocation.getLatitude(), sourceLocation.getLongitude(),
			//			destLocation.getLatitude(), destLocation.getLongitude());
				double distance = Haversine.calculateDistanceMeters(sourceLocation.getLatitude(), sourceLocation.getLongitude(),
						destLocation.getLatitude(), destLocation.getLongitude());
				
				// Path loss
				double pathLoss = calculate5GPathLoss(distance);

				// Signal-to-Noise Ratio (SNR) calculation
				double noisePower = NOISE_DENSITY + 10 * Math.log10(bw) + NOISE_FIGURE; // dBm

				double snr = TX_POWER + ANTENNA_GAIN - pathLoss - noisePower; // dB (include antenna gain)

				// Ensure SNR is positive
				if (snr < 0) {
					snr = 1; // Minimum SNR to avoid unrealistic values
				}

				// Transmission rate (Shannon's capacity formula with MIMO gain)
				double capacity = bw * Math.log10(1 + Math.pow(10, snr / 10)) / Math.log10(2); // bits per second
				
				double transmissionRate = capacity / 8 * 1; // bytes per second (MIMO gain)
				
				if (log)
					SimLogger.getInstance().addDelayLog(
							new DelayLogItem(time, task.getMobileDeviceId(), false, pathLoss, distance, transmissionRate));

				// Transmission delay
				double transmissionDelay = taskSizeInBits / transmissionRate;

				// Propagation delay
				double propagationDelay = distance / SPEED_OF_LIGHT;

				// Queueing delay (M/M/1 model)
				/*double lamda = 1 / fiveGMMPPForDownload.getPoissonMean(); // Arrival rate (tasks per second)
				double mu = transmissionRate / taskSize; // Service rate (tasks per second)
				
				double queueingDelay = (mu > lamda * deviceCount) ? 1 / (mu - lamda * deviceCount) : 0; // Avoid negative or
																										// infinite delay
				*/
				double queueingDelay = calculateMM1(taskSize, capacity, fiveGMMPPForDownload, justEstimate);
				
				// Total delay
				return transmissionDelay + propagationDelay + queueingDelay;
		/*double bw = SimSettings.getInstance().get5GBandwidth();

		double distance = calculateDistance(sourceLocation.getLatitude(), sourceLocation.getLongitude(),
				destLocation.getLatitude(), destLocation.getLongitude());

		double pathLoss = calculate5GPathLoss(distance);

		int transmittedPower = 30;

		double signalStrength = transmittedPower - pathLoss;

		if (log)
			SimLogger.getInstance().addDelayLog(
					new DelayLogItem(time, task.getMobileDeviceId(), false, pathLoss, distance, signalStrength));

		double effectiveBandwidth = bw / Math.pow(10, pathLoss / 10); // Adjusting bandwidth based on path loss

		double result = calculateMM1(taskSize, effectiveBandwidth, fiveGMMPPForDownload, justEstimate);

		if (max5GDelay < result) {
			max5GDelay = result;
		}

		return result;*/
	}

	private double get5GUploadDelay(double taskSize, boolean log, double time, Location sourceLocation,
			Location destLocation, boolean justEstimate) {

		double taskSizeInBits = taskSize * 1000 * 8;
				
		double bw = SimSettings.getInstance().get5GBandwidth();

//		double distance = calculateDistance(sourceLocation.getLatitude(), sourceLocation.getLongitude(), destLocation.getLatitude(), destLocation.getLongitude());
		double distance = Haversine.calculateDistanceMeters(sourceLocation.getLatitude(), sourceLocation.getLongitude(),
				destLocation.getLatitude(), destLocation.getLongitude());

		double pathLoss = calculate5GPathLoss(distance);
		
		// Signal-to-Noise Ratio (SNR) calculation
		double noisePower = NOISE_DENSITY + 10 * Math.log10(bw) + NOISE_FIGURE; // dBm
		double snr = TX_POWER + ANTENNA_GAIN - pathLoss - noisePower; // dB (include antenna gain)

		// Ensure SNR is positive
		if (snr < 0) {
			snr = 1; // Minimum SNR to avoid unrealistic values
		}

		// Transmission rate (Shannon's capacity formula with MIMO gain)
		double capacity = bw * Math.log10(1 + Math.pow(10, snr / 10)) / Math.log10(2); // bits per second

		double transmissionRate = capacity / 8 * 1; // bytes per second (MIMO gain)

		if (log)
			SimLogger.getInstance().addDelayLog(
					new DelayLogItem(time, task.getMobileDeviceId(), true, pathLoss, distance, transmissionRate));
		// Transmission delay
		double transmissionDelay = (taskSizeInBits)/ transmissionRate;

		// Propagation delay
		double propagationDelay = distance / SPEED_OF_LIGHT;

		// Queueing delay (M/M/1 model)
		/*double lamda = 1 / fiveGMMPPForDownload.getPoissonMean(); // Arrival rate (tasks per second)
		double mu = transmissionRate / taskSize; // Service rate (tasks per second)
		
		double queueingDelay = (mu > lamda * deviceCount) ? 1 / (mu - lamda * deviceCount) : 0; // Avoid negative or
																								// infinite delay
		*/
		double queueingDelay = calculateMM1(taskSize, capacity, fiveGMMPPForUpload, justEstimate);

		return transmissionDelay + propagationDelay + queueingDelay;
		
		/*
		double d2 = Haversine.calculateDistance(sourceLocation.getLatitude(), sourceLocation.getLongitude(),
				destLocation.getLatitude(), destLocation.getLongitude());

		double pathLoss = calculate5GPathLoss(distance);
		double p2 = calculate5GPathLoss(d2);

		int transmittedPower = 30;

		double signalStrength = transmittedPower - pathLoss;
		
		// Signal-to-Noise Ratio (SNR) calculation
		//double noisePower = NOISE_DENSITY + 10 * Math.log10(bw) + NOISE_FIGURE; // dBm
		//double signalStrength = TX_POWER + ANTENNA_GAIN - pathLoss - noisePower; // dB (include antenna gain)

		if (log)
			SimLogger.getInstance().addDelayLog(
					new DelayLogItem(time, task.getMobileDeviceId(), true, pathLoss, distance, signalStrength));

		double e = bw / pathLoss;
		double effectiveBandwidth = bw / Math.pow(10, pathLoss / 10); // Adjusting bandwidth based on path loss
		double e2 = bw / Math.pow(10, p2 / 10); // Adjusting bandwidth based on path loss

		double r = calculateMM1(taskSize, e, fiveGMMPPForUpload, justEstimate);
		double r2 = calculateMM1(taskSize, e2, fiveGMMPPForUpload, justEstimate);
		double result = calculateMM1(taskSize, effectiveBandwidth, fiveGMMPPForUpload, justEstimate);

		if (max5GDelay < result) {
			max5GDelay = result;
		}

		return result;
		*/
	}

//	private double calculate5GPathLoss(double distance) {
//		double frequency = SimSettings.getInstance().get5GBandwidth(); // FREQUENCY_5G
//		double fspl = 20 * Math.log10((4 * Math.PI * frequency * 1e9) / SPEED_OF_LIGHT); // Free Space Path Loss
//		double pl = fspl + 10 * PATH_LOSS_EXPONENT * Math.log10(distance) + SHADOW_FADING_STD_DEV;
//		return pl;
//	}

	private double calculate5GPathLoss(double distance) {
		double frequency = SimSettings.getInstance().get5GBandwidth(); // Frequency in Hz
		double fspl = 20 * Math.log10((4 * Math.PI * distance * frequency) / SPEED_OF_LIGHT); // Free Space Path Loss in
																								// dB
		double pl = fspl + 10 * PATH_LOSS_EXPONENT * Math.log10(distance) + SHADOW_FADING_STD_DEV;
		return pl;
		
		/*if (distance < REFERENCE_DISTANCE) {
			distance = REFERENCE_DISTANCE; // Avoid log10(0)
		}

		double freeSpacePathLoss = 20 * Math.log10(distance) + 20 * Math.log10(frequency) - 147.55;

		double pathLoss = freeSpacePathLoss
				+ 10 * VEHICULAR_PATH_LOSS_EXPONENT * Math.log10(distance / REFERENCE_DISTANCE);

		//TODO change getGaussianRandom(0, SHADOWING_STD_DEV) to 
		double shadowing = SimUtils.getRandomDoubleNumber(0, SHADOWING_STD_DEV); // Shadowing effect

		return pathLoss + shadowing;*/
	}

	/**
	 * 
	 * @param lat1
	 * @param lon1
	 * @param lat2
	 * @param lon2
	 * @return distance in meters
	 */

	private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS * c; // Distance in meters
	}

	public void updateMM1QueeuModel() {
		int numOfAccessPoint = SimSettings.getInstance().getNumOfEdgeDatacenters();

		double lastInterval = CloudSim.clock() - lastMM1QueeuUpdateTime;
		lastMM1QueeuUpdateTime = CloudSim.clock();

		// GENERATE BACKGROUD TRAFFIC ON MAN RESOURCE
		// assume that each edge server sends/receives control
		// message to/from edge orchestrator periodically
		double numOfControlMessagePerInterval = lastInterval * (double) numberOfMobileDevices
				* MAN_CONTROL_MESSAGE_PER_SECONDS;

		double sizeOfControlMessages = (double) numberOfMobileDevices * MAN_CONTROL_MESSAGE_SIZE;

		// UPDATE MM1 QUEUE MODEL VARIABLES to simulate mmpp/m/1 queue model
		// for wlan:
		for (int i = 0; i < numOfAccessPoint; i++) {
			wlanMMPPForDownload[i].updateMM1Values(lastInterval, 0, 0);
			wlanMMPPForUpload[i].updateMM1Values(lastInterval, 0, 0);

			if (getWlanDownloadDelay(0, i, true) != 0)
				wlanMMPPForDownload[i].updateLastSuccessfulMM1QueueValues();
			if (getWlanUploadDelay(0, i, true) != 0)
				wlanMMPPForUpload[i].updateLastSuccessfulMM1QueueValues();
		}

		// for man:
		manMMPPForDownload.updateMM1Values(lastInterval, numOfControlMessagePerInterval, sizeOfControlMessages);
		manMMPPForUpload.updateMM1Values(lastInterval, numOfControlMessagePerInterval, sizeOfControlMessages);
		if (getManDownloadDelay(0, true) != 0)
			manMMPPForDownload.updateLastSuccessfulMM1QueueValues();
		if (getManUploadDelay(0, true) != 0)
			manMMPPForUpload.updateLastSuccessfulMM1QueueValues();

		// for wan:
		wanMMPPForDownload.updateMM1Values(lastInterval, 0, 0);
		wanMMPPForUpload.updateMM1Values(lastInterval, 0, 0);
		if (getWanDownloadDelay(0, true) != 0)
			wanMMPPForDownload.updateLastSuccessfulMM1QueueValues();
		if (getWanUploadDelay(0, true) != 0)
			wanMMPPForUpload.updateLastSuccessfulMM1QueueValues();

		/*
		 * // for gsm: gsmMMPPForDownload.updateMM1Values(lastInterval, 0, 0);
		 * gsmMMPPForUpload.updateMM1Values(lastInterval, 0, 0); if
		 * (getGsmDownloadDelay(0, true) != 0)
		 * gsmMMPPForDownload.updateLastSuccessfulMM1QueueValues(); if
		 * (getGsmUploadDelay(0, true) != 0)
		 * gsmMMPPForUpload.updateLastSuccessfulMM1QueueValues();
		 */

		// for 5G:
		fiveGMMPPForDownload.updateMM1Values(lastInterval, 0, 0);
		fiveGMMPPForUpload.updateMM1Values(lastInterval, 0, 0);

		if (this.task == null)
			return;

		Location sourceLoc = task.getSubmittedLocation();
		Location desLoca = getDestinationLocByTask(task, sourceLoc);

		if (get5GDownloadDelay(0, false, 0.0, sourceLoc, desLoca, true) != 0) {
			fiveGMMPPForDownload.updateLastSuccessfulMM1QueueValues();
		}
		if (get5GUploadDelay(0, false, 0.0, sourceLoc, desLoca, true) != 0) {
			fiveGMMPPForUpload.updateLastSuccessfulMM1QueueValues();
		}

		// for(int i = 0; i< numOfAccessPoint; i++){
		// SimLogger.printLine(CloudSim.clock() + ": MM1 Queue Model is updated");
		// SimLogger.printLine("WlanPoissonMeanForDownload[" + i + "] -
		// avgWlanTaskOutputSize[" + i + "]: "
		// + String.format("%.3f", wlanMMPPForDownload[i].getPoissonMean()) + " - "
		// + String.format("%.3f", wlanMMPPForDownload[i].getTaskSize()));
		// SimLogger.printLine("WlanPoissonMeanForUpload[" + i + "] -
		// avgWlanTaskInputSize[" + i + "]: "
		// + String.format("%.3f", wlanMMPPForUpload[i].getPoissonMean()) + " - "
		// + String.format("%.3f", wlanMMPPForUpload[i].getTaskSize()));
		// }
		// SimLogger.printLine("ManPoissonMeanForDownload - avgManTaskOutputSize: "
		// + String.format("%.3f", manMMPPForDownload.getPoissonMean()) + " - "
		// + String.format("%.3f", manMMPPForDownload.getTaskSize()));
		// SimLogger.printLine("ManPoissonMeanForUpload - avgManTaskInputSize: "
		// + String.format("%.3f", manMMPPForUpload.getPoissonMean()) + " - "
		// + String.format("%.3f", manMMPPForUpload.getTaskSize()));
		// SimLogger.printLine("WanPoissonMeanForDownload - avgWanTaskOutputSize: "
		// + String.format("%.3f", wanMMPPForDownload.getPoissonMean()) + " - "
		// + String.format("%.3f", wanMMPPForDownload.getTaskSize()));
		// SimLogger.printLine("WanPoissonMeanForUpload - avgWanTaskInputSize: "
		// + String.format("%.3f", wanMMPPForUpload.getPoissonMean()) + " - "
		// + String.format("%.3f", wanMMPPForUpload.getTaskSize()));
		// SimLogger.printLine("GsmPoissonMeanForDownload - avgGsmTaskOutputSize: "
		// + String.format("%.3f", gsmMMPPForDownload.getPoissonMean()) + " - "
		// + String.format("%.3f", gsmMMPPForDownload.getTaskSize()));
		// SimLogger.printLine("GsmPoissonMeanForUpload - avgGsmTaskInputSize: "
		// + String.format("%.3f", gsmMMPPForUpload.getPoissonMean()) + " - "
		// + String.format("%.3f", gsmMMPPForUpload.getTaskSize()));
		// SimLogger.printLine("------------------------------------------------");

	}

	public VehicularEdgeServerManager getEdgeServerManager() {

		return (VehicularEdgeServerManager) SimManager.getInstance().getEdgeServerManager();
	}

//	public void setEdgeServerManager(VehicularEdgeServerManager edgeServerManager) {
//		this.edgeServerManager = edgeServerManager;
//	}
}
