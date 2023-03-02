package dev.slimevr.bridge;

import dev.slimevr.Main;
import dev.slimevr.bridge.ProtobufMessages.*;
import dev.slimevr.tracking.trackers.Tracker;
import dev.slimevr.tracking.trackers.TrackerRole;
import dev.slimevr.util.ann.VRServerThread;
import io.eiren.util.ann.Synchronize;
import io.eiren.util.ann.ThreadSafe;
import io.eiren.util.collections.FastList;
import io.eiren.util.logging.LogManager;
import io.github.axisangles.ktmath.Vector3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;


public abstract class ProtobufBridge implements Bridge {
	private static final String resetSourceNamePrefix = "ProtobufBridge";

	@VRServerThread
	protected final List<Tracker> sharedTrackers = new FastList<>();
	protected final String bridgeName;
	@ThreadSafe
	private final Queue<ProtobufMessage> inputQueue = new LinkedBlockingQueue<>();
	@ThreadSafe
	private final Queue<ProtobufMessage> outputQueue = new LinkedBlockingQueue<>();
	@Synchronize("self")
	private final Map<String, Tracker> remoteTrackersBySerial = new HashMap<>();
	@Synchronize("self")
	private final Map<Integer, Tracker> remoteTrackersByTrackerId = new HashMap<>();
	private final Tracker hmd;
	private boolean hadNewData = false;
	private Tracker hmdTracker;

	public ProtobufBridge(String bridgeName, Tracker hmd) {
		this.bridgeName = bridgeName;
		this.hmd = hmd;
	}

	@BridgeThread
	protected abstract boolean sendMessageReal(ProtobufMessage message);

	@BridgeThread
	protected void messageReceived(ProtobufMessage message) {
		inputQueue.add(message);
	}

	@ThreadSafe
	protected void sendMessage(ProtobufMessage message) {
		outputQueue.add(message);
	}

	@BridgeThread
	protected void updateMessageQueue() {
		ProtobufMessage message = null;
		while ((message = outputQueue.poll()) != null) {
			if (!sendMessageReal(message))
				return;
		}
	}

	@VRServerThread
	@Override
	public void dataRead() {
		hadNewData = false;
		ProtobufMessage message = null;
		while ((message = inputQueue.poll()) != null) {
			processMessageReceived(message);
			hadNewData = true;
		}
		if (hadNewData && hmdTracker != null) {
			// If HMD was DISCONNECTED, need to rebuild the skeleton, as it
			// ignores the HMD if it's DISCONNECTED in order to support using
			// other trackers on head.
			if (hmd.getStatus() == dev.slimevr.tracking.trackers.TrackerStatus.DISCONNECTED)
				Main.getVrServer().updateSkeletonModel();
			trackerOverrideUpdate(hmdTracker, hmd);
		}
	}

	@VRServerThread
	protected void trackerOverrideUpdate(Tracker source, Tracker target) {
		target.setPosition(source.getPosition());
		target.setRotation(source.getRotation());
		target.setStatus(source.getStatus());
		target.dataTick();
	}

	@VRServerThread
	@Override
	public void dataWrite() {
		if (!hadNewData) // Don't write anything if no message were received, we
							// always process at the
			// speed of the other side
			return;
		for (Tracker tracker : sharedTrackers) {
			writeTrackerUpdate(tracker);
		}
	}

	@VRServerThread
	protected void writeTrackerUpdate(Tracker localTracker) {
		Position.Builder builder = Position.newBuilder().setTrackerId(localTracker.getId());
		if (localTracker.getHasPosition()) {
			builder.setX(localTracker.getPosition().getX());
			builder.setY(localTracker.getPosition().getY());
			builder.setZ(localTracker.getPosition().getZ());
		}
		if (localTracker.getHasRotation()) {
			builder.setQx(localTracker.getRotation().getX());
			builder.setQy(localTracker.getRotation().getY());
			builder.setQz(localTracker.getRotation().getZ());
			builder.setQw(localTracker.getRotation().getW());
		}
		sendMessage(ProtobufMessage.newBuilder().setPosition(builder).build());
	}

	@VRServerThread
	protected void processMessageReceived(ProtobufMessage message) {
		// if(!message.hasPosition())
		// LogManager.log.info("[" + bridgeName + "] MSG: " + message);
		if (message.hasPosition()) {
			positionReceived(message.getPosition());
		} else if (message.hasUserAction()) {
			userActionReceived(message.getUserAction());
		} else if (message.hasTrackerStatus()) {
			trackerStatusReceived(message.getTrackerStatus());
		} else if (message.hasTrackerAdded()) {
			trackerAddedReceived(message.getTrackerAdded());
		}
	}

	@VRServerThread
	protected void positionReceived(Position positionMessage) {
		Tracker tracker = getInternalRemoteTrackerById(positionMessage.getTrackerId());
		if (tracker != null) {
			if (positionMessage.hasX())
				tracker
					.setPosition(
						new Vector3(
							positionMessage.getX(),
							positionMessage.getY(),
							positionMessage.getZ()
						)
					);
			tracker
				.setRotation(
					new io.github.axisangles.ktmath.Quaternion(
						positionMessage.getQw(),
						positionMessage.getQx(),
						positionMessage.getQy(),
						positionMessage.getQz()
					)

				);
			tracker.dataTick();
		}
	}

	@VRServerThread
	protected abstract Tracker createNewTracker(TrackerAdded trackerAdded);

	@VRServerThread
	protected void trackerAddedReceived(TrackerAdded trackerAdded) {
		Tracker tracker = getInternalRemoteTrackerById(trackerAdded.getTrackerId());
		if (tracker != null) {
			// TODO reinit?
			return;
		}
		tracker = createNewTracker(trackerAdded);
		synchronized (remoteTrackersBySerial) {
			remoteTrackersBySerial.put(tracker.getName(), tracker);
		}
		synchronized (remoteTrackersByTrackerId) {
			remoteTrackersByTrackerId.put(tracker.getId(), tracker);
		}
		if (trackerAdded.getTrackerRole() == TrackerRole.HMD.getId()) {
			hmdTracker = tracker;
		} else {
			Main.getVrServer().registerTracker(tracker);
		}
	}

	@VRServerThread
	protected void userActionReceived(UserAction userAction) {
		String resetSourceName = "%s: %s".formatted(resetSourceNamePrefix, bridgeName);
		switch (userAction.getName()) {
			case "calibrate":
				LogManager
					.warning("[" + bridgeName + "] Received deprecated user action 'calibrate'!");
			case "reset":
				// TODO : Check pose field
				Main.getVrServer().resetTrackersFull(resetSourceName);
				break;
			case "fast_reset":
				Main.getVrServer().resetTrackersYaw(resetSourceName);
				break;
		}
	}

	@VRServerThread
	protected void trackerStatusReceived(TrackerStatus trackerStatus) {
		Tracker tracker = getInternalRemoteTrackerById(trackerStatus.getTrackerId());
		if (tracker != null) {
			tracker
				.setStatus(
					dev.slimevr.tracking.trackers.TrackerStatus
						.getById(trackerStatus.getStatusValue())
				);
		}
	}

	@ThreadSafe
	protected Tracker getInternalRemoteTrackerById(int trackerId) {
		synchronized (remoteTrackersByTrackerId) {
			return remoteTrackersByTrackerId.get(trackerId);
		}
	}

	@VRServerThread
	protected void reconnected() {
		for (Tracker tracker : sharedTrackers) {
			TrackerAdded.Builder builder = TrackerAdded
				.newBuilder()
				.setTrackerId(tracker.getId())
				.setTrackerName(tracker.getName())
				.setTrackerSerial(tracker.getName())
				.setTrackerRole(tracker.getTrackerPosition().getTrackerRole().getId());
			sendMessage(ProtobufMessage.newBuilder().setTrackerAdded(builder).build());
		}
	}

	@VRServerThread
	protected void disconnected() {
		synchronized (remoteTrackersByTrackerId) {
			for (Entry<Integer, Tracker> integerTEntry : remoteTrackersByTrackerId.entrySet()) {
				integerTEntry
					.getValue()
					.setStatus(dev.slimevr.tracking.trackers.TrackerStatus.DISCONNECTED);
			}
		}
		if (hmdTracker != null) {
			hmd.setStatus(dev.slimevr.tracking.trackers.TrackerStatus.DISCONNECTED);
		}
	}

	@VRServerThread
	@Override
	public void addSharedTracker(Tracker tracker) {
		if (sharedTrackers.contains(tracker))
			return;
		sharedTrackers.add(tracker);
		TrackerAdded.Builder builder = TrackerAdded
			.newBuilder()
			.setTrackerId(tracker.getId())
			.setTrackerName(tracker.getName())
			.setTrackerSerial(tracker.getName())
			.setTrackerRole(tracker.getTrackerPosition().getTrackerRole().getId());
		sendMessage(ProtobufMessage.newBuilder().setTrackerAdded(builder).build());
	}

	@VRServerThread
	@Override
	public void removeSharedTracker(Tracker tracker) {
		// Remove shared tracker
		sharedTrackers.remove(tracker);

		// Set the tracker's status as disconnected
		TrackerStatus.Builder statusBuilder = TrackerStatus
			.newBuilder()
			.setTrackerId(tracker.getId());
		statusBuilder.setStatus(TrackerStatus.Status.DISCONNECTED);
		sendMessage(ProtobufMessage.newBuilder().setTrackerStatus(statusBuilder).build());
	}
}
