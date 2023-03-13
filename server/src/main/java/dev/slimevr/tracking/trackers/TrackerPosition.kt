package dev.slimevr.tracking.trackers

import solarxr_protocol.datatypes.BodyPart

/**
 * Represents a position on the body that a tracker could be placed. Any bone is
 * a valid position.
 *
 * TrackerPosition intentionally lacks a numerical id to avoid breakage.
 */
enum class TrackerPosition(
	val designation: String,
	val trackerRole: TrackerRole?,
	val bodyPart: Int,
) {
	HEAD("body:head", TrackerRole.HMD, BodyPart.HEAD),
	NECK("body:neck", TrackerRole.NECK, BodyPart.NECK),
	CHEST("body:chest", TrackerRole.CHEST, BodyPart.CHEST),
	WAIST("body:waist", null, BodyPart.WAIST),
	HIP("body:hip", TrackerRole.WAIST, BodyPart.HIP),
	LEFT_UPPER_LEG("body:left_upper_leg", TrackerRole.LEFT_KNEE, BodyPart.LEFT_UPPER_LEG),
	RIGHT_UPPER_LEG("body:right_upper_leg", TrackerRole.RIGHT_KNEE, BodyPart.RIGHT_UPPER_LEG),
	LEFT_LOWER_LEG("body:left_lower_leg", null, BodyPart.LEFT_LOWER_LEG),
	RIGHT_LOWER_LEG("body:right_lower_leg", null, BodyPart.RIGHT_LOWER_LEG),
	LEFT_FOOT("body:left_foot", TrackerRole.LEFT_FOOT, BodyPart.LEFT_FOOT),
	RIGHT_FOOT("body:right_foot", TrackerRole.RIGHT_FOOT, BodyPart.RIGHT_FOOT),
	LEFT_LOWER_ARM("body:left_lower_arm", null, BodyPart.LEFT_LOWER_ARM),
	RIGHT_LOWER_ARM("body:right_lower_arm", null, BodyPart.RIGHT_LOWER_ARM),
	LEFT_UPPER_ARM("body:left_upper_arm", TrackerRole.LEFT_ELBOW, BodyPart.LEFT_UPPER_ARM),
	RIGHT_UPPER_ARM("body:right_upper_arm", TrackerRole.RIGHT_ELBOW, BodyPart.RIGHT_UPPER_ARM),
	LEFT_HAND("body:left_hand", TrackerRole.LEFT_CONTROLLER, BodyPart.LEFT_HAND),
	RIGHT_HAND("body:right_hand", TrackerRole.RIGHT_CONTROLLER, BodyPart.RIGHT_HAND),
	LEFT_SHOULDER("body:left_shoulder", TrackerRole.LEFT_SHOULDER, BodyPart.LEFT_SHOULDER),
	RIGHT_SHOULDER("body:right_shoulder", TrackerRole.RIGHT_SHOULDER, BodyPart.RIGHT_SHOULDER),
	;

	companion object {
		/** Indexed by `BodyPart` int value. EFFICIENCY FTW  */
		private val byBodyPart: Array<out TrackerPosition?> = Array(BodyPart.names.size) { i -> values()[i] } // FIXME
		private val byDesignation = values().associateBy { it.designation.lowercase() }
		private val byTrackerRole = values().filter { it.trackerRole != null }.associateBy { it.trackerRole!! }

		/**
		 * Gets the `TrackerPosition` by its string designation.
		 *
		 * @return Returns an optional as not all strings are valid designators.
		 */
		@JvmStatic
		fun getByDesignation(designation: String): TrackerPosition? = byDesignation[designation.lowercase()]

		@JvmStatic
		fun getByTrackerRole(role: TrackerRole): TrackerPosition? = byTrackerRole[role]

		@JvmStatic
		fun getByBodyPart(bodyPart: Int): TrackerPosition? = byBodyPart[bodyPart]
	}
}
