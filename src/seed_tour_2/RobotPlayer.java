package seed_tour_2;
import battlecode.common.*;

import java.util.ArrayList;

@SuppressWarnings({"JavaDoc", "InfiniteLoopStatement", "WeakerAccess"})
public strictfp class RobotPlayer {
    static RobotController rc;

    // General variables
    static Direction prevDir = null;
    static int NUM_ARCHONS;
    static MapLocation[] ENEMY_ARCHON_LOCS;
    static Team ENEMY;
    static Team FRIENDLY;

    // Broadcast index limits
    static int ARCHON_MIN_BROADCAST_IDX = 0;
    static int ARCHON_MAX_BROADCAST_IDX = 99;
    static int GARDENER_MIN_BROADCAST_IDX = 100;
    static int GARDENER_MAX_BROADCAST_IDX = 299;
    static int LUMBERJACK_MIN_BROADCAST_IDX = 300;
    static int LUMBERJACK_MAX_BROADCAST_IDX = 399;

    // Archon variables
    enum ArchonPhase {
        ERROR (-1), FIND_LEADER (1), FLEE (2), BUILD (3);

        private int phaseNum;
        ArchonPhase(int phaseNum) { this.phaseNum = phaseNum; }
        int phaseToNum() { return this.phaseNum; }
        static ArchonPhase numToPhase(int desiredPhaseNum) {
            for (ArchonPhase phase: ArchonPhase.values()) {
                if (phase.phaseNum == desiredPhaseNum) return phase;
            }
            return ERROR;
        }
    }
    enum ArchonStatus {
        ERROR (-1), TRAPPED (1), CRAMPED (2), SURROUNDED (3), FREE (4);

        private int priorityNum;
        ArchonStatus(int priorityNum) { this.priorityNum = priorityNum; }
        int statusToNum() { return this.priorityNum; }
        static ArchonStatus numToStatus(int desiredPriorityNum) {
            for (ArchonStatus status: ArchonStatus.values()) {
                if (status.priorityNum == desiredPriorityNum) return status;
            }
            return ERROR;
        }
    }
    static int ARCHON_ID_MIN_IDX;
    static int ARCHON_LEADER_ID_IDX;
    static int ARCHON_LEADER_PRIORITY_IDX;
    static int ARCHON_PHASE_IDX;
    static int ARCHON_PRIORITY_MIN_IDX;
    static int GARDENER_COOLDOWN = 30;
    static float GARDENER_THRESHOLD = RobotType.GARDENER.bulletCost * 1.2f;
    static int LAST_GARDENER_SETTLED_IDX; // Don't build new gardener until old one settled
    static int turnsSinceGardener = GARDENER_COOLDOWN + 1;

    // Gardener variables
    static ArrayList<MapLocation> freeGardenerLocs = new ArrayList<>();
    static Direction gap = null;
    static int GARDENER_WRITE_LOCS_IDX; // Index where to write new possible gardener locs
    static int GARDENER_LOCS_MIN_IDX;
    static int SOLDIER_COOLDOWN = 20;
    static float SOLDIER_THRESHOLD = RobotType.SOLDIER.bulletCost * 2.0f;
    static int gardenerLocIdx; // Broadcast index of current desired gardener location
    static int numHiredLumberjacks = 0;
    static int turnsSinceSoldier = SOLDIER_COOLDOWN + 1;

    // Lumberjack variables
    static int LUMBERJACK_HIRES_MIN_IDX;
    static int LUMBERJACK_WRITE_HIRES_IDX; // Index where to write new lumberjack hires

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        // Initialize general variables
        FRIENDLY = rc.getTeam();
        ENEMY = FRIENDLY.opponent();
        ENEMY_ARCHON_LOCS = rc.getInitialArchonLocations(ENEMY);
        NUM_ARCHONS = ENEMY_ARCHON_LOCS.length;

        // Initialize archon variables
        ARCHON_PHASE_IDX = ARCHON_MIN_BROADCAST_IDX;
        ARCHON_LEADER_ID_IDX = ARCHON_PHASE_IDX + 1;
        ARCHON_LEADER_PRIORITY_IDX = ARCHON_LEADER_ID_IDX + 1;
        ARCHON_PRIORITY_MIN_IDX = ARCHON_LEADER_PRIORITY_IDX + 1;
        ARCHON_ID_MIN_IDX = ARCHON_PRIORITY_MIN_IDX + NUM_ARCHONS;
        LAST_GARDENER_SETTLED_IDX = ARCHON_ID_MIN_IDX + NUM_ARCHONS;
        if (rc.getRoundNum() < 3) {
            rc.broadcastInt(LAST_GARDENER_SETTLED_IDX, -1);
        }

        // Initialize gardener variables
        GARDENER_WRITE_LOCS_IDX = GARDENER_MIN_BROADCAST_IDX;
        GARDENER_LOCS_MIN_IDX = GARDENER_WRITE_LOCS_IDX + 1;
        if (rc.readBroadcastInt(GARDENER_WRITE_LOCS_IDX) == 0) {
            rc.broadcastInt(GARDENER_WRITE_LOCS_IDX, GARDENER_LOCS_MIN_IDX);
        }
        gardenerLocIdx = GARDENER_LOCS_MIN_IDX;

        // Initialize lumberjack variables
        LUMBERJACK_HIRES_MIN_IDX = LUMBERJACK_MIN_BROADCAST_IDX;
        LUMBERJACK_WRITE_HIRES_IDX = LUMBERJACK_HIRES_MIN_IDX;

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case SCOUT:
                runScout();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case TANK:
                runTank();
                break;
        }
    }

    /* ******************** BEGIN ARCHON CODE ******************** */

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        ArchonPhase currentPhase;

        rc.broadcastInt(ARCHON_PHASE_IDX, ArchonPhase.FIND_LEADER.phaseToNum());

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                currentPhase = ArchonPhase.numToPhase(rc.readBroadcastInt(ARCHON_PHASE_IDX));

                switch (currentPhase) {
                    // Phase 1: Get priority of this archon. If priority of all archons found, determine "leader"
                    // TODO: switch phase to FIND_INIT_LEADER again when leader archon dies
                    case FIND_LEADER:
                        System.out.println("Phase: Find leader");
                        getArchonPriority();
                        if (findArchonLeader()) {
                            System.out.println("Found leader.");
                            rc.broadcastInt(ARCHON_PHASE_IDX, ArchonPhase.BUILD.phaseToNum());
                            currentPhase = ArchonPhase.BUILD;
                        }
                        else break;
                    case BUILD:
                        // Only build if this archon is the leader
                        if (rc.getID() == rc.readBroadcastInt(ARCHON_LEADER_ID_IDX)) {
                            System.out.println("Phase: Build");
                            archonBuild();
                        }
                        break;
                    // TODO: Add flee logic and condition to trigger flee
                    case FLEE:
                        System.out.println("Phase: Flee");
                        break;
                    case ERROR: default:
                        System.out.println("ERROR: No Archon phase!");
                }

                tryMove(randomDirection());

                // Shake a tree if possible
                tryShake();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Try to build a gardener
     *
     * @throws GameActionException
     */
    // TODO: Find a way to make archon not trap itself when building gardeners
    static void archonBuild() throws GameActionException {
        int lastGardenerSettled = rc.readBroadcastInt(LAST_GARDENER_SETTLED_IDX);
        System.out.println("Last gardener settled int: " + lastGardenerSettled);
        System.out.println("Current round num: " + rc.getRoundNum());
        if (turnsSinceGardener > GARDENER_COOLDOWN && rc.getTeamBullets() > GARDENER_THRESHOLD &&
                lastGardenerSettled != rc.getRoundNum() - 1) { // Relies on archons going before gardeners
            if (tryBuildUnit(RobotType.GARDENER) != null) {
                turnsSinceGardener = 0;
                rc.broadcastInt(LAST_GARDENER_SETTLED_IDX, rc.getRoundNum());
            }
        }
        turnsSinceGardener++;
    }

    /**
     * Find the "leader archon" that builds all the units based on highest broadcasted priority.
     *
     * @return Whether the leader archon was broadcasted
     * @throws GameActionException
     */
    static boolean findArchonLeader() throws GameActionException {
        System.out.println("Finding leader");
        int highestPriority = -1;
        int highestPriorityId = -1;
        for (int i = 0; i < NUM_ARCHONS; i++) {
            int currRobotPriority = rc.readBroadcastInt(ARCHON_PRIORITY_MIN_IDX + i);
            if (currRobotPriority == 0) return false; // List of archon priorities not complete yet
            else if (currRobotPriority > highestPriority) {
                highestPriority = currRobotPriority;
                highestPriorityId = rc.readBroadcastInt(ARCHON_ID_MIN_IDX + i);

            }
        }
        rc.broadcastInt(ARCHON_LEADER_ID_IDX, highestPriorityId);
        rc.broadcastInt(ARCHON_LEADER_PRIORITY_IDX, highestPriority);

        return true;
    }

    /**
     * Find and broadcast the priority and id of this archon (higher number means more priority).
     *
     * @throws GameActionException
     */
    // FIXME: Reduce bytecode cost (currently ~1500)
    static void getArchonPriority() throws GameActionException {
        System.out.println("Getting archon priority.");

        MapLocation myLoc = rc.getLocation();

        // Determine broadcast indices
        int PRIORITY_IDX = -1;
        int ID_IDX = -1;
        for (int i = 0; i < NUM_ARCHONS; i++) {
            if (rc.readBroadcastInt(ARCHON_PRIORITY_MIN_IDX + i) == 0) {
                PRIORITY_IDX = ARCHON_PRIORITY_MIN_IDX + i;
                ID_IDX = ARCHON_ID_MIN_IDX + i;
                break;
            }
        }

        // Check how many directions archon can move in
        int numFreeDirs = 0;
        float TEST_DIST = RobotType.ARCHON.strideRadius;
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(new Direction((float) (Math.PI / 4 * i)), TEST_DIST)) {
                numFreeDirs++;
            }
        }

        // Archon completely surrounded by trees and can't move
        if (numFreeDirs == 0) {
            rc.broadcastInt(PRIORITY_IDX, ArchonStatus.TRAPPED.statusToNum());
            System.out.println("Can't move, priority 1.");
        }
        else {
            // Archon near enemy archon
            float DETECT_RADIUS = RobotType.ARCHON.sensorRadius * 0.75f;
            RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(DETECT_RADIUS, rc.getTeam().opponent());
            boolean nearbyEnemyArchon = false;
            for (RobotInfo enemyRobot: nearbyEnemyRobots) {
                if (enemyRobot.type == RobotType.ARCHON) {
                    nearbyEnemyArchon = true;
                    break;
                }
            }
            if (nearbyEnemyArchon) {
                rc.broadcastInt(PRIORITY_IDX, ArchonStatus.CRAMPED.statusToNum());
                System.out.println("Near enemy archon, priority 2.");
            }
            else {
                // Archon completely surrounded by trees but can move/build units
                float DIST_TO_CIRCLE = RobotType.ARCHON.bodyRadius + RobotType.GARDENER.bodyRadius +
                        GameConstants.BULLET_TREE_RADIUS;
                float CIRCLE_RADIUS = RobotType.GARDENER.bodyRadius + (GameConstants.BULLET_TREE_RADIUS * 2);
                int clearCircles = 0;
                for (int i = 0; i < 8; i++) {
                    MapLocation circleCenter = myLoc.add((float) (i * Math.PI / 4), DIST_TO_CIRCLE);
                    if (!rc.onTheMap(circleCenter, CIRCLE_RADIUS)) continue;
                    TreeInfo[] circleTrees = rc.senseNearbyTrees(circleCenter, CIRCLE_RADIUS, Team.NEUTRAL);
                    if (circleTrees.length == 0) clearCircles++; // FIXME: Add break statement if clearCircles == 0 stays
                }
                System.out.println("Number of clear circles: " + clearCircles);
                if (clearCircles == 0) {
                    rc.broadcastInt(PRIORITY_IDX, ArchonStatus.SURROUNDED.statusToNum());
                    System.out.println("Surrounded by trees, priority 3");
                }
                // All other situations
                else {
                    rc.broadcastInt(PRIORITY_IDX, ArchonStatus.FREE.statusToNum());
                    System.out.println("Other, priority 4.");
                }
            }
        }

        rc.broadcastInt(ID_IDX, rc.getID());
    }

    /* ******************** END ARCHON CODE ******************** */

    /* ******************** BEGIN GARDENER CODE ******************** */

    static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        ArchonStatus leaderArchonStatus = ArchonStatus.numToStatus(rc.readBroadcastInt(
                ARCHON_LEADER_PRIORITY_IDX));
        ArrayList<Integer> freedBroadcastIdxs = new ArrayList<>();
        boolean buildDisruptiveScout = true;
        boolean foundSpotFromBroadcast = false;
        boolean settled = false;
        int cnt = GARDENER_LOCS_MIN_IDX;
        int DISRUPTIVE_SCOUT_ROUND_LIMIT = 50;
        MapLocation myLoc = rc.getLocation();
        MapLocation prevLoc = myLoc;
        MapLocation prevPrevLoc = null;
        MapLocation targetLoc = null;

        // Find all viable
        float x = rc.readBroadcastFloat(cnt);
        float y = rc.readBroadcastFloat(cnt + 1);
        while (x != 0 && y != 0) {
            freeGardenerLocs.add(new MapLocation(x, y));
            cnt += 2;
            x = rc.readBroadcastFloat(cnt);
            y = rc.readBroadcastFloat(cnt + 1);
        }
        targetLoc = findNewGardenerSpot();
        // 1st gardener or no viable gardener spots
        if (targetLoc == null) {
            targetLoc = myLoc;
        }
        else {
            foundSpotFromBroadcast = true;
        }

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                // FIXME: Broadcast while not settled so archon knows if gardener died before settling
                while (!settled) {
                    // Settle gardener in place, broadcast possible neighbors, and prepare to build
                    if (targetLoc == myLoc) {
                        System.out.println("Settled in place.");
                        // Attempts to see if other gardeners can be built around this one
                        // Note it doesn't guarantee, since sight radius is shorter than desired circle for the other gardeners
                        int writeLocsIdx = rc.readBroadcastInt(GARDENER_WRITE_LOCS_IDX);
                        for (int i = 0; i < 4; i++) {
                            MapLocation checkLoc = myLoc.add((float) ( Math.PI / 2 * i),
                                    RobotType.GARDENER.sensorRadius - 0.01f); // FIXME: FUCK FLOATING POINT
                            rc.setIndicatorDot(checkLoc, 0, 0, 0);
                            if (rc.onTheMap(checkLoc)) {
                                float GAP_SIZE = 3.0f;
                                MapLocation intendedLoc = myLoc.add((float) ( Math.PI / 2 * i),
                                        2 * RobotType.GARDENER.bodyRadius + 4 * GameConstants.BULLET_TREE_RADIUS +
                                                GAP_SIZE);
                                rc.broadcastFloat(writeLocsIdx, intendedLoc.x);
                                rc.broadcastFloat(writeLocsIdx + 1, intendedLoc.y);
                                writeLocsIdx += 2;
                            }
                        }
                        rc.broadcastInt(GARDENER_WRITE_LOCS_IDX, writeLocsIdx);
                        // If no viable spots, don't replace location
                        if (foundSpotFromBroadcast) {
                            rc.broadcastFloat(gardenerLocIdx, -1);
                            rc.broadcastFloat(gardenerLocIdx + 1, -1);
                        }
                        settled = true;
                        rc.broadcastInt(LAST_GARDENER_SETTLED_IDX, -1);
                        System.out.println("Last gardener settled int: -1");

                        // Hire lumberjack to clear surrounding area
                        while (tryBuildUnit(RobotType.LUMBERJACK) == null) {
                            Clock.yield();
                        }
                    }
                    else {
                        System.out.println("Moving towards viable gardener loc.");

                        // If spot isn't possible, pick a new one.
                        if (rc.canSenseAllOfCircle(targetLoc, RobotType.GARDENER.bodyRadius) &&
                                !rc.onTheMap(targetLoc, RobotType.GARDENER.bodyRadius)) {
                            targetLoc = findNewGardenerSpot();
                            if (targetLoc == null) {
                                targetLoc = myLoc;
                            }
                        }

                        prevPrevLoc = prevLoc;
                        prevLoc = myLoc;
                        tryMoveAdvanced(targetLoc, 10, 17);
                        myLoc = rc.getLocation();
                        System.out.println(myLoc + " |||| " + prevLoc + " |||| " + prevPrevLoc);
                        // FIXME: Wobbling gardeners
                        if ((myLoc.distanceTo(prevPrevLoc) < 0.1f || myLoc.distanceTo(prevLoc) < 0.1f) &&
                                rc.senseNearbyTrees(2.0f, Team.NEUTRAL).length > 0) {
                            gardenerClearPath(targetLoc);
                        }
                        rc.broadcastInt(LAST_GARDENER_SETTLED_IDX, rc.getRoundNum());
                        System.out.println("Last gardener settled int: " + rc.readBroadcastInt(LAST_GARDENER_SETTLED_IDX));
                        rc.setIndicatorDot(targetLoc, 255, 0, 0);
                        Clock.yield(); // Can't do anything
                    }
                }

                // Build a disruptive scout if very early in the game
                while (buildDisruptiveScout) {
                    System.out.println("Trying to build disruptive scout.");
                    if (rc.getRoundNum() < DISRUPTIVE_SCOUT_ROUND_LIMIT) {
                        if (tryBuildUnit(RobotType.SCOUT) != null) {
                            System.out.println("Built disruptive scout.");
                            buildDisruptiveScout = false;
                        }
                        else {
                            System.out.println("Waiting to build disruptive scout");
                            Clock.yield();
                        }
                    }
                    else {
                        buildDisruptiveScout = false;
                        System.out.println("No time to build disruptive scout, move on.");
                    }
                }


//                System.out.println("Leader archon status: " + leaderArchonStatus);
//                switch (leaderArchonStatus) {
//                    // TODO: Add cramped logic
//                    case CRAMPED:
//                        break;
//                    // Add surrounded logic
//                    case SURROUNDED:
//                        growGarden();
//                        break;
//                    case FREE:
                        soldierRush();
//                        break;
//                }

                // Shake a tree if possible
                tryShake();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Find a new viable gardener spot.
     *
     * @return The new viable gardener spot, null if no such spot is found.
     * @throws GameActionException
     */
    static MapLocation findNewGardenerSpot() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        while (freeGardenerLocs.size() > 0) {
            MapLocation testLoc = freeGardenerLocs.remove(0);
            // Test if target location can actually hold the gardener and is still not occupied
            // Not a guarantee that gardener can actually be at that location if out of sensor radius
            if ((testLoc.x == -1 || testLoc.y == -1) ||
                    (myLoc.distanceTo(testLoc) < RobotType.GARDENER.sensorRadius - RobotType.GARDENER.bodyRadius &&
                            !rc.onTheMap(testLoc, RobotType.GARDENER.bodyRadius))) {
                gardenerLocIdx += 2;
            }
            else {
                return testLoc;
            }
        }
        return null;
    }

    /**
     * If gardener cannot reach desired location, get a lumberjack and clear the path.
     *
     * @param targetLoc Desired target location to clear towards
     * @throws GameActionException
     */
    static void gardenerClearPath(MapLocation targetLoc) throws GameActionException {
        if (numHiredLumberjacks == 0) {
            RobotInfo hiredLumberjack = tryBuildUnit(RobotType.LUMBERJACK);
            if (hiredLumberjack != null) {
                numHiredLumberjacks++;
                rc.broadcastInt(LUMBERJACK_WRITE_HIRES_IDX, hiredLumberjack.getID());
                rc.broadcastFloat(LUMBERJACK_WRITE_HIRES_IDX + 1, targetLoc.x);
                rc.broadcastFloat(LUMBERJACK_WRITE_HIRES_IDX + 2, targetLoc.y);
                LUMBERJACK_WRITE_HIRES_IDX += 3;
            }
        }
    }

    /**
     * Attempt to grow hextrees in a grid.
     * Rushes soldiers
     *
     * @throws GameActionException
     */
    static void soldierRush() throws GameActionException {

        // Plant a tree if possible (always leave a gap for building units)
        for (int i=0;i<6;i++) {
            Direction dir = new Direction((float) (i * Math.PI / 3));
            if (gap == null) {
                if (rc.canPlantTree(dir)) {
                    gap = dir;
                }
            }
            // Check that direction is not the gap
            else if (!gap.equals(dir, (float) 1e-3) && rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                break;
            }
        }

        // Water lowest tree
        TreeInfo[] teamTrees = rc.senseNearbyTrees(GameConstants.BULLET_TREE_RADIUS*2, FRIENDLY);
        if (teamTrees.length > 0) {
            TreeInfo lowestTree = teamTrees[0];
            for (TreeInfo tree : teamTrees) {
                if (tree.health < lowestTree.health) {
                    lowestTree = tree;
                }
            }
            if (rc.canWater(lowestTree.getID())) {
                rc.water(lowestTree.getID());
            }
        }

        if (turnsSinceSoldier > SOLDIER_COOLDOWN && rc.getTeamBullets() > SOLDIER_THRESHOLD) {
            if (tryBuildUnit(RobotType.SOLDIER) != null) {
                turnsSinceSoldier = 0;
            }
        }
        turnsSinceSoldier++;
    }

    /* ******************** END GARDENER CODE ******************** */

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");

        int cnt = LUMBERJACK_HIRES_MIN_IDX;
        int compareId = rc.readBroadcastInt(cnt);
        MapLocation prevLoc = null;
        MapLocation prevPrevLoc = null;
        MapLocation targetLoc = null;
        while (compareId != 0) {
            if (compareId == rc.getID()) {
                targetLoc = new MapLocation(rc.readBroadcastFloat(cnt + 1), rc.readBroadcastFloat(cnt + 2));
                break;
            }
            cnt += 3;
            compareId = rc.readBroadcastInt(cnt);
        }

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                // Strike nearby enemy units
                RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (nearbyEnemyRobots.length > 0) {
                    System.out.println("Sensed enemy units.");
                    RobotInfo nearbyEnemy = nearbyEnemyRobots[0];
                    MapLocation nearbyEnemyLoc = nearbyEnemy.location;
                    tryMove(nearbyEnemyLoc);
                    if (MapLocation.doCirclesCollide(rc.getLocation(), GameConstants.LUMBERJACK_STRIKE_RADIUS,
                            nearbyEnemyLoc, nearbyEnemy.getRadius()) &&
                            rc.canStrike()) {
                        rc.strike();
                        System.out.println("Strike enemy unit.");
                    }
                    TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                    if (nearbyNeutralTrees.length > 0) {
                        System.out.println("Chop nearby tree.");
                        TreeInfo nearestTree = nearbyNeutralTrees[0];
                        if (rc.canChop(nearestTree.getID())) {
                            rc.chop(nearestTree.getID());
                        }

                    }
                    else {
                        System.out.println("Nothing to do, move randomly.");
                        tryMove(randomDirection(), 20, 8);
                    }
                }
                else {
                    // Help gardener who hired it to clear a path for it
                    if (targetLoc != null) {

                        // If gardener settled or is dead (since you are at its intended loc), do something else.
//                        if (rc.readBroadcastBoolean(LAST_GARDENER_SETTLED_IDX) || rc.getLocation().equals(targetLoc)) {
                        System.out.println(rc.readBroadcastInt(LAST_GARDENER_SETTLED_IDX));
                        if (rc.readBroadcastInt(LAST_GARDENER_SETTLED_IDX) != Clock.getBytecodeNum()) { // Relies on lumberjack always going after gardener
                            targetLoc = null;
                        }
                        else {
                            System.out.println("Help gardener settle by clearing.");
                            prevPrevLoc = prevLoc;
                            prevLoc = rc.getLocation();
                            tryMoveAdvanced(targetLoc, 10, 17);
                            MapLocation myLoc = rc.getLocation();
                            if (myLoc.distanceTo(prevPrevLoc) < 0.1f || myLoc.distanceTo(prevLoc) < 0.1f) {
                                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(RobotType.LUMBERJACK.bodyRadius +
                                        GameConstants.INTERACTION_DIST_FROM_EDGE, Team.NEUTRAL);
                                if (nearbyTrees.length > 0) {
                                    TreeInfo nearestTree = nearbyTrees[0];
                                    if (rc.canChop(nearestTree.getID())) {
                                        rc.chop(nearestTree.getID());
                                    }
                                }
                            }
                        }
                    }
                    else {
                        TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                        if (nearbyNeutralTrees.length > 0) {
                            System.out.println("Chop nearby tree.");
                            TreeInfo nearestTree = nearbyNeutralTrees[0];
                            if (rc.canChop(nearestTree.getID())) {
                                rc.chop(nearestTree.getID());
                            }
                            else {
                                tryMoveAdvanced(nearestTree.getLocation(), 20, 8);
                            }
                        }
                        else {
                            System.out.println("Nothing to do, move randomly.");
                            tryMove(randomDirection(), 20, 8);
                        }
                    }
                }

                // Shake a tree if possible
                tryShake();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /* ******************** BEGIN SCOUT CODE ******************** */

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");

        // The code you want your robot to perform every round should be in this loop
        RobotInfo target;
        MapLocation targetLoc;
        TreeInfo hidingTree = null; // The tree that the scout hides in to shoot enemies
        int currentArchonIndex = 0; // Current enemy archon we are tracking

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

                // Reset target because it can die
                target = null;
                targetLoc = null;

                // Record location (obviously)
                MapLocation myLoc = rc.getLocation();

                // Search for nearby enemy robots
                RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(-1, ENEMY);
                // Priority is gardener > archon > others > tank, then distance for same RobotType
                if (nearbyEnemyRobots.length > 0) {
                    // The following variables are initialized to give least priority
                    RobotType targetType = RobotType.TANK;
                    float targetDist = RobotType.SCOUT.sensorRadius + 1;
                    int targetPriority = getScoutTargetPriority(targetType);
                    // Find target with most priority
                    for (RobotInfo rb : nearbyEnemyRobots) {
                        RobotType rbType = rb.type;
                        int rbPriority = getScoutTargetPriority(rbType);
                        if (rbPriority == -1) continue; // Ignore unit
                        if (rbPriority > targetPriority || (rbPriority == targetPriority &&
                                myLoc.distanceTo(rb.location) < targetDist)) {
                            target = rb;
                            targetLoc = target.location;
                            targetType = target.type;
                            targetDist = targetLoc.distanceTo(myLoc);
                            targetPriority = getScoutTargetPriority(targetType);
                        }
                    }
                    if (target != null) System.out.println("Found nearby target.");
                }

                if (target == null) {
                    // Couldn't find nearby robots, find the initial position of a enemy archon
                    if (currentArchonIndex < NUM_ARCHONS) {
                        System.out.println("Find location of enemy archon.");
                        targetLoc = ENEMY_ARCHON_LOCS[currentArchonIndex];
                        target = new RobotInfo(-1, ENEMY, RobotType.ARCHON,
                                targetLoc, RobotType.ARCHON.maxHealth,
                                0, 0);
                    }
                    // No enemy archons to track anymore, random location
                    else {
                        System.out.println("Pick random location.");
                        targetLoc = myLoc.add(randomDirection(), RobotType.SCOUT.strideRadius);
                        target = new RobotInfo(-2, ENEMY, RobotType.TANK, targetLoc,
                                RobotType.TANK.maxHealth, 0, 0);
                    }
                }
                rc.setIndicatorDot(targetLoc, 0, 255, 0);

                // Prepare to attack if target loc is in sight (and we are not tracking based on old archon data)
                if (rc.canSenseLocation(targetLoc) && target.getID() >= 0) {
                    System.out.println("Target loc in range.");
                    // Attempt to find a hiding tree if one is not available
//                    if (hidingTree == null) {
                    System.out.println("No hiding tree, finding one.");
                    TreeInfo[] nearbyEnemyTrees = rc.senseNearbyTrees(targetLoc,
                            RobotType.SCOUT.sensorRadius / 3.0f, ENEMY);
                    // If there are nearby enemy trees, find the one closest to target
                    if (nearbyEnemyTrees.length > 0) {
                        System.out.println("Nearby trees to hide in.");
                        TreeInfo closestTree = nearbyEnemyTrees[0];
                        float closestTreeDist = targetLoc.distanceTo(closestTree.location);
                        for (TreeInfo tree : nearbyEnemyTrees) {
                            float thisTreeDist = targetLoc.distanceTo(tree.location);
                            if (thisTreeDist < closestTreeDist) {
                                closestTree = tree;
                                closestTreeDist = thisTreeDist;
                            }
                        }
                        hidingTree = closestTree;
                    }
//                    }

                    // If there is a hiding tree, hide in it
                    if (hidingTree != null) {
                        System.out.println("Hiding in hiding tree");
                        MapLocation intendedLoc = hidingTree.location.add(hidingTree.location.directionTo(targetLoc),
                                GameConstants.GENERAL_SPAWN_OFFSET / 2);
                        rc.setIndicatorDot(intendedLoc, 0, 0, 0);
                        tryMove(intendedLoc);
                        myLoc = rc.getLocation();
                    }
                    // Still can't find a tree. Move towards target (or move in circle if close enough)
                    else {
                        System.out.println("No hiding tree, still moving.");
                        if (!dodgeBullets()) {
                            tryMove(targetLoc);
                        }
                        myLoc = rc.getLocation();
                    }

                    // Shoot at target
                    float MAX_TARGET_DIST = 5.0f;
                    if (rc.canFireSingleShot() && myLoc.distanceTo(targetLoc) < MAX_TARGET_DIST) {
                        System.out.println("Shot at target.");
                        rc.setIndicatorLine(myLoc, targetLoc, 0, 0, 0);
                        rc.fireSingleShot(myLoc.directionTo(targetLoc));
                    }
                }
                // Else dodge bullets or move towards the target
                else {
                    System.out.println("Target not in range, moving to it.");
//                    tryMove(targetLoc);
//                    myLoc = rc.getLocation();
//                    rc.setIndicatorLine(myLoc, targetLoc, 0, 255, 0);
                    if (!dodgeBullets()) {
                        tryMove(targetLoc);
                    }
                    else {
                        System.out.println("Dodging bullets instead");
                    }
                    myLoc = rc.getLocation();
                    // If nothing's there, we were tracking an archon that already left or random loc
                    // In that case, increment the tracked archon
                    if (myLoc.isWithinDistance(targetLoc, 3)) {
                        currentArchonIndex += 1;
                    }
                }

                // Shake a tree if possible
                tryShake();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Scout Exception!");
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the priority of targets for the scout.
     *
     * @param rt The type of robot in question
     * @return The priority of that robot. A higher number means higher priority. -1 means ignore.
     */
    static int getScoutTargetPriority(RobotType rt) {
        switch (rt) {
            case ARCHON: return -1;
            case GARDENER: return 1;
            case LUMBERJACK: return -1;
            case SOLDIER: return -1;
            case SCOUT: return -1;
            case TANK: return -1;
        }
        return -1;
    }

    /* ******************** END SCOUT CODE ******************** */

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");

        MapLocation myLoc = rc.getLocation();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                myLoc = rc.getLocation();

                tryMove(randomDirection());

                myLoc = rc.getLocation();

                // Shoot enemy if possible
                RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (nearbyEnemyRobots.length > 0 && rc.canFireTriadShot()) {
                    RobotInfo[] nearbyAllyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
                    for (RobotInfo nearbyEnemyRobot : nearbyEnemyRobots) {
                        boolean validShot = true;
                        // Check if allies are in the way
//                        if (nearbyAllyRobots.length > 0) {
//                            for (RobotInfo nearbyAllyRobot : nearbyAllyRobots) {
//                                if (myLoc.directionTo(nearbyEnemyRobot.location).degreesBetween(myLoc.directionTo(
//                                        nearbyAllyRobot.location)) < GameConstants.TRIAD_SPREAD_DEGREES) {
//                                    validShot = false;
//                                    break;
//                                }
//                            }
//                        }
                        if (validShot) {
                            rc.fireTriadShot(myLoc.directionTo(nearbyEnemyRobot.location));
                            break;
                        }
                    }
                }

                // Shake a tree if possible
                tryShake();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    // TODO: Add tank functionality
    static void runTank() throws GameActionException {
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

//                tryMove(randomDirection());
//
//                // Shake a tree if possible
//                tryShake();
                rc.disintegrate();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    /* ******************** Below are extra methods that the robots above use. ******************** */

    /**
     * Robot donates bullets if there are extra.
     * @throws GameActionException
     */
    // FIXME: Currently only considers end game. Change to consider donating during the game.
    static void checkDonate() throws GameActionException {
//        if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * rc.getVictoryPointCost() ||
//                rc.getRoundNum() == rc.getRoundLimit() - 1) {
//            rc.donate(rc.getTeamBullets());
//        }
        float DONATE_THRESHOLD = 1000;
        if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * rc.getVictoryPointCost() ||
                rc.getRoundNum() == rc.getRoundLimit() - 1) {
            rc.donate(rc.getTeamBullets());
        }
        else if (rc.getTeamBullets() - DONATE_THRESHOLD > rc.getVictoryPointCost()) {
            int numVPEarned = (int) ((rc.getTeamBullets() - DONATE_THRESHOLD) / rc.getVictoryPointCost());
            rc.donate(numVPEarned * rc.getVictoryPointCost());
        }
    }

    /**
     * Attempt to dodge nearby bullets
     *
     * @return true if there are bullets nearby to dodge
     * @throws GameActionException
     */
    static boolean dodgeBullets() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        final float MAX_SENSE_RANGE = Math.min(rc.getType().bulletSightRadius, 10);
        BulletInfo[] nearbyBullets = rc.senseNearbyBullets(MAX_SENSE_RANGE);
        if (nearbyBullets.length > 0) {
            MapLocation intendedLoc = rc.getLocation();
            for (BulletInfo bullet : nearbyBullets) {
                Direction bulletDir = bullet.dir;
                MapLocation p1 = bullet.location;
                // If the bullet is moving away and is farther than stride radius, ignore
                if (p1.distanceTo(myLoc) > rc.getType().strideRadius &&
                        bulletDir.radiansBetween(p1.directionTo(myLoc)) > Math.PI / 2) continue;
                MapLocation p2 = p1.add(bulletDir, bullet.speed);
                float xDiff = p2.x - p1.x;
                float yDiff = p2.y - p1.y;

                // Calculate smallest vector between myLoc and bullet trajectory
                float distance = (float) (Math.abs((yDiff * myLoc.x) - (xDiff * myLoc.y) + (p2.x * p1.y) - (p2.y * p1.x)) /
                        Math.sqrt((yDiff * yDiff) + (xDiff * xDiff)));
                Direction dir = (bulletDir.degreesBetween(p1.directionTo(myLoc)) > 0) ?
                        bulletDir.rotateLeftDegrees(90) : bulletDir.rotateRightDegrees(90);
                // Actual vector increases when distance decreases linearly
                System.out.println(distance + " " + (MAX_SENSE_RANGE - distance) + " ||| ");
//                        rc.setIndicatorLine(myLoc, myLoc.add(dir, 10 - distance), 0, 0, 0);
                intendedLoc = intendedLoc.add(dir, MAX_SENSE_RANGE - distance);
            }

            // Avoid map edges
            // FIXME: Improve map edge avoidance
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction((float) (i * Math.PI / 4));
                if (!rc.onTheMap(myLoc.add(dir, rc.getType().strideRadius))) {
                    intendedLoc = intendedLoc.add(dir.opposite(), MAX_SENSE_RANGE * 2);
                }
            }

            rc.setIndicatorLine(myLoc, intendedLoc, 0, 0, 0);
            rc.setIndicatorDot(myLoc, 255, 0, 0);
            tryMove(intendedLoc, 10, 17);
            return true;
        }
        return false;
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Try to build this unit
     *
     * @param rbType The type of robot we're trying to build
     * @return The RobotInfo of the robot built, or null if no robot was built
     * @throws GameActionException
     */
    static RobotInfo tryBuildUnit(RobotType rbType) throws GameActionException {
        for (int i=0;i<6;i++) {
            Direction dir = new Direction((float) (i * Math.PI / 3));
            if (rc.canBuildRobot(rbType, dir)) {
                rc.buildRobot(rbType, dir);
                return rc.senseRobotAtLocation(rc.getLocation().add(dir, rc.getType().bodyRadius +
                        GameConstants.GENERAL_SPAWN_OFFSET + rbType.bodyRadius));
            }
        }
        return null;
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param loc The intended location
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(MapLocation loc) throws GameActionException {
        return tryMove(loc,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param loc The intended location
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(MapLocation loc, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(loc)) {
            rc.move(loc);
            return true;
        }

        Direction dir = rc.getLocation().directionTo(loc);
        float distance = rc.getLocation().distanceTo(loc);

        // Now try a bunch of similar angles
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck), distance)) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck), distance);
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck), distance)) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck), distance);
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /** Attempt to move to a location. Trees and gardeners are treated as circles to avoid.
     *
     * @param loc The intended location
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMoveAdvanced(MapLocation loc, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(loc)) {
            rc.move(loc);
            return true;
        }

        MapLocation myLoc = rc.getLocation();
        Direction dir = rc.getLocation().directionTo(loc);
        float distance = rc.getLocation().distanceTo(loc);
        float bufferDist = RobotType.GARDENER.bodyRadius + 2 * GameConstants.BULLET_TREE_RADIUS + rc.getType().bodyRadius;

        RobotInfo[] nearbyTeamRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        ArrayList<RobotInfo> nearbyGardenersArrayList = new ArrayList<>();
        for (RobotInfo robot : nearbyTeamRobots) {
            if (robot.type == RobotType.GARDENER) {
                nearbyGardenersArrayList.add(robot);
            }
        }
        System.out.println("Attempting advanced move.");
        System.out.println(nearbyGardenersArrayList.size());
        RobotInfo[] nearbyGardeners = nearbyGardenersArrayList.toArray(new RobotInfo[0]);
        System.out.println(nearbyGardeners.length);

        // Now try a bunch of similar angles
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            MapLocation desiredLoc = myLoc.add(dir.rotateLeftDegrees(degreeOffset*currentCheck), distance);
            if(rc.canMove(desiredLoc)) {
                boolean possible = true;
                for (RobotInfo nearbyGardener : nearbyGardeners) {
                    if (nearbyGardener.location.distanceTo(desiredLoc) < bufferDist) {
                        possible = false;
                        break;
                    }
                }
                if (possible) {
                    rc.move(desiredLoc);
                    return true;
                }
            }
            desiredLoc = myLoc.add(dir.rotateRightDegrees(degreeOffset*currentCheck), distance);
            // Try the offset on the right side
            if(rc.canMove(desiredLoc)) {
                boolean possible = true;
                for (RobotInfo nearbyGardener : nearbyGardeners) {
                    if (nearbyGardener.location.distanceTo(desiredLoc) < bufferDist) {
                        possible = false;
                        break;
                    }
                }
                if (possible) {
                    rc.move(desiredLoc);
                    return true;
                }
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * Try to shake a nearby tree. Every unit should do this.
     *
     * @return true if a tree was shaken
     * @throws GameActionException
     */
    static boolean tryShake() throws GameActionException {
        TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(rc.getType().bodyRadius +
                GameConstants.INTERACTION_DIST_FROM_EDGE, Team.NEUTRAL);
        for (TreeInfo tree : nearbyNeutralTrees) {
            if (rc.canShake(tree.getID()) && tree.containedBullets > 0) {
                rc.shake(tree.getID());
                return true;
            }
        }
        return false;
    }
}
