package qualify_tour_10;
import battlecode.common.*;
import java.util.ArrayList;

@SuppressWarnings({"JavaDoc", "InfiniteLoopStatement", "WeakerAccess"})
public strictfp class RobotPlayer {
    static RobotController rc;

    // General variables
    static int numArchons;
    static MapLocation[] ENEMY_ARCHON_LOCS;
    static Team ENEMY;
    static Team FRIENDLY;

    // Bug pathing variables
    static Direction prevDir = null; // Direction robot was travelling while bug pathing in previous step.
    static MapLocation mLineStart = null; // Initial location at initial call to bugpath to location

    // Broadcast index limits
    static int ARCHON_MIN_BROADCAST_IDX = 0;
    static int ARCHON_MAX_BROADCAST_IDX = 99;
    static int GARDENER_MIN_BROADCAST_IDX = 100;
    static int LUMBERJACK_MIN_BROADCAST_IDX = 300;
    static int SOLDIER_MIN_BROADCAST_IDX = 400;

    // Archon variables
    enum ArchonPhase {
        ERROR (-1), FIND_INIT_LEADER (1), FIND_NEW_LEADER (2), BUILD (3);

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
        ERROR (-1), TRAPPED (1), CRAMPED (2), CLOSE (5), SURROUNDED (3), FREE (4);

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
    static int ARCHON_PHASE_IDX; // Index of global archon phase (doesn't apply to flee)
    static int ARCHON_LEADER_ID_IDX; // Index of leader archon's id
    static int ARCHON_LEADER_STATUS_IDX; // Status of leader archon
    static int ARCHON_LEADER_UPDATE_IDX; // Broadcast round num every n rounds to know leader is still alive
    static int ARCHON_LEADER_UPDATE_THRESHOLD = 5;
    static int ARCHON_IDS_MIN_IDX; // Min index of array of archon ids
    static int ARCHON_PRIORITIES_MIN_IDX; // Min index of array of archon priorities
    static int BUILD_GARDENER_COOLDOWN = 75;
    static float BUILD_GARDENER_THRESHOLD = RobotType.GARDENER.bulletCost * 1.2f;
    static int LAST_GARDENER_SETTLED_IDX; // Don't build new gardener until old one settled
    static int turnsSinceGardener = 100000;

    // Gardener variables
    static ArrayList<MapLocation> freeGardenerLocs = new ArrayList<>(); // Possible new gardener locations
    static Direction gap = null; // Gap for gardener to build units
    static int GARDENER_LOCS_MIN_IDX; // Min index of array of possible gardener locations
    static int SOLDIER_COOLDOWN = 50;
    static float SOLDIER_THRESHOLD = RobotType.SOLDIER.bulletCost * 1.5f;
    static int TANK_COOLDOWN = 75;
    static float TANK_THRESHOLD = RobotType.TANK.bulletCost * 1.5f;
    static int gardenerLocIdx; // Broadcast index of current desired gardener location
    static int turnsSinceSoldier = 100000;
    static int turnsSinceTank = 100000;


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
        numArchons = ENEMY_ARCHON_LOCS.length;

        // Initialize archon variables
        ARCHON_PHASE_IDX = ARCHON_MIN_BROADCAST_IDX;
        ARCHON_LEADER_ID_IDX = ARCHON_PHASE_IDX + 1;
        ARCHON_LEADER_STATUS_IDX = ARCHON_LEADER_ID_IDX + 1;
        ARCHON_LEADER_UPDATE_IDX = ARCHON_LEADER_STATUS_IDX + 1;
        LAST_GARDENER_SETTLED_IDX = ARCHON_LEADER_UPDATE_IDX + 1;
        if (rc.getRoundNum() < 3) {
            rc.broadcastInt(LAST_GARDENER_SETTLED_IDX, -1);
        }

        // Initialize archon array variables at end
        ARCHON_PRIORITIES_MIN_IDX = ARCHON_MAX_BROADCAST_IDX - numArchons + 1;
        ARCHON_IDS_MIN_IDX = ARCHON_PRIORITIES_MIN_IDX - numArchons;

        // Initialize gardener variables
        GARDENER_LOCS_MIN_IDX = GARDENER_MIN_BROADCAST_IDX ;
        gardenerLocIdx = GARDENER_LOCS_MIN_IDX;

        // Here, we've separated the controls into a different method for each RobotType.
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

        ArchonPhase currPhase;
        int archonLeaderId;
        int currRoundNum;
        int myId = rc.getID();

        rc.broadcastInt(ARCHON_PHASE_IDX, ArchonPhase.FIND_INIT_LEADER.phaseToNum());

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                archonLeaderId = rc.readBroadcastInt(ARCHON_LEADER_ID_IDX);
                currPhase = ArchonPhase.numToPhase(rc.readBroadcastInt(ARCHON_PHASE_IDX));
                currRoundNum = rc.getRoundNum();

                // Check that leader is alive
                if (rc.getRoundNum() % ARCHON_LEADER_UPDATE_THRESHOLD == 0) {
                    // As leader, broadcast round # every n rounds to show that leader is still alive.
                    if (myId == archonLeaderId) {
                        rc.broadcastInt(ARCHON_LEADER_UPDATE_IDX, rc.getRoundNum());
                    }
                    // As non-leader, if leader's not alive, find new leader
                    else {
                        if (currRoundNum - rc.readBroadcastInt(ARCHON_LEADER_UPDATE_IDX) >
                                ARCHON_LEADER_UPDATE_THRESHOLD) {
                            rc.broadcastInt(ARCHON_PHASE_IDX, ArchonPhase.FIND_NEW_LEADER.phaseToNum());
                            currPhase = ArchonPhase.FIND_NEW_LEADER;
                            // Reset arrays of archon ids and priorities
                            for (int i = 0; i < numArchons; i++) {
                                rc.broadcastInt(ARCHON_IDS_MIN_IDX + i ,0);
                                rc.broadcastInt(ARCHON_PRIORITIES_MIN_IDX + i, 0);
                            }
                        }
                    }
                }

                switch (currPhase) {
                    case FIND_INIT_LEADER:
                        System.out.println("Phase: Find initial leader");
                        getArchonPriority();
                        if (findArchonLeader()) {
                            System.out.println("Found initial leader.");
                            rc.broadcastInt(ARCHON_PHASE_IDX, ArchonPhase.BUILD.phaseToNum());
                            currPhase = ArchonPhase.BUILD;
                        }
                        break;
                    case FIND_NEW_LEADER:
                        System.out.println("Phase: Find new leader");
                        getArchonPriority();
                        if (findArchonLeader()) {
                            System.out.println("Found new leader.");
                            rc.broadcastInt(ARCHON_PHASE_IDX, ArchonPhase.BUILD.phaseToNum());
                        }
                        // Allow all archons to decrement number of archon correctly
                        else {
                            numArchons -= 1;
                        }
                        break;
                    case BUILD:
                        // Only build if this archon is the leader
                        if (rc.getID() == archonLeaderId) {
                            System.out.println("Phase: Build");
                            archonBuild();
                        }
                        break;
                    case ERROR: default:
                        System.out.println("ERROR: No Archon phase!");
                }

                tryMove(randomDirection());

                // Shake a tree if possible
                tryShake();

                // Fire pixel art!
                appreciateArt();

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
     * @return true if gardener was built
     * @throws GameActionException
     */
    static boolean archonBuild() throws GameActionException {
        int lastGardenerSettled = rc.readBroadcastInt(LAST_GARDENER_SETTLED_IDX);
        MapLocation myLoc = rc.getLocation();
        // If last gardener is still settling, return immediately and don't increment
        // Relies on archons going before gardeners
        if (lastGardenerSettled == rc.getRoundNum() - 1) return false;

        ArchonStatus leaderArchonStatus = ArchonStatus.numToStatus(rc.readBroadcastInt(ARCHON_LEADER_STATUS_IDX));
        // If enemy archon is relatively close, build gardeners less and soldiers more.
        if (leaderArchonStatus == ArchonStatus.CLOSE || leaderArchonStatus == ArchonStatus.CRAMPED) {
            BUILD_GARDENER_COOLDOWN = 200;
        }

        // Build gardener if conditions are met
        if (turnsSinceGardener > BUILD_GARDENER_COOLDOWN && rc.getTeamBullets() > BUILD_GARDENER_THRESHOLD) {
            // Build in general direction of enemy archon, so it tries not to build units that trap themselves
            // Make sure reference direction is multiple of 5.
            float referenceDegrees = ((int) (myLoc.directionTo(ENEMY_ARCHON_LOCS[0]).getAngleDegrees() / 5)) * 5;
            Direction referenceDir = new Direction((float) (referenceDegrees * Math.PI / 180));

            for (int i = 0; i < 36; i++) {
                Direction dir = referenceDir.rotateLeftDegrees(i * 5);
                if (rc.canBuildRobot(RobotType.GARDENER, dir)) {
                    rc.buildRobot(RobotType.GARDENER, dir);
                    turnsSinceGardener = 0;
                    return true;
                }
                dir = referenceDir.rotateRightDegrees(i * 5);
                if (rc.canBuildRobot(RobotType.GARDENER, dir)) {
                    rc.buildRobot(RobotType.GARDENER, dir);
                    turnsSinceGardener = 0;
                    return true;
                }
            }
        }
        turnsSinceGardener++;
        return false;
    }

    /**
     * Draw fire pixel art on the archons for fun :D
     */
    static void appreciateArt() {
        MapLocation myLoc = rc.getLocation();

        rc.setIndicatorDot(new MapLocation(myLoc.x - 3.0f, myLoc.y - 4.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 3.0f, myLoc.y - 3.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 3.0f, myLoc.y - 2.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 3.0f, myLoc.y - 1.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 3.0f, myLoc.y + 0), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 3.0f, myLoc.y + 1.0f), 178, 34, 34);

        rc.setIndicatorDot(new MapLocation(myLoc.x - 2.0f, myLoc.y - 4.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 2.0f, myLoc.y - 3.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 2.0f, myLoc.y - 2.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 2.0f, myLoc.y - 1.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 2.0f, myLoc.y + 0), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 2.0f, myLoc.y + 1.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 2.0f, myLoc.y + 2.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 2.0f, myLoc.y + 3.0f), 178, 34, 34);

        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y - 4.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y - 3.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y - 2.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y - 1.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y + 0), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y + 1.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y + 2.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y + 3.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y + 4.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x - 1.0f, myLoc.y + 5.0f), 178, 34, 34);

        rc.setIndicatorDot(myLoc.add(new Direction(0f, -4.0f), 4f), 255, 215, 0);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, -3.0f), 3f), 255, 215, 0);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, -2.0f), 2f), 255, 215, 0);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, -1.0f), 1f), 255, 215, 0);
        rc.setIndicatorDot(myLoc, 255, 215, 0);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, 1.0f), 1.0f), 255, 215, 0);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, 2.0f), 2.0f), 255, 215, 0);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, 3.0f), 3.0f), 255, 215, 0);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, 4.0f), 4.0f), 255, 165, 0);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, 5.0f), 5.0f), 178, 34, 34);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, 6.0f), 6.0f), 178, 34, 34);
        rc.setIndicatorDot(myLoc.add(new Direction(0f, 7.0f), 7.0f), 178, 34, 34);

        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y - 4.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y - 3.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y - 2.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y - 1.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y + 0), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y + 1.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y + 2.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y + 3.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y + 4.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y + 5.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 1.0f, myLoc.y + 6.0f), 178, 34, 34);

        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y - 4.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y - 3.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y - 2.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y - 1.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y + 0), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y + 1.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y + 2.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y + 3.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 2.0f, myLoc.y + 4.0f), 178, 34, 34);

        rc.setIndicatorDot(new MapLocation(myLoc.x + 3.0f, myLoc.y - 4.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 3.0f, myLoc.y - 3.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 3.0f, myLoc.y - 2.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 3.0f, myLoc.y - 1.0f), 178, 34, 34);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 3.0f, myLoc.y + 0), 178, 34, 34);

        rc.setIndicatorDot(new MapLocation(myLoc.x + 4.0f, myLoc.y - 4.0f), 255, 215, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 4.0f, myLoc.y - 3.0f), 255, 165, 0);
        rc.setIndicatorDot(new MapLocation(myLoc.x + 4.0f, myLoc.y - 2.0f), 178, 34, 34);
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
        for (int i = 0; i < numArchons; i++) {
            int currRobotPriority = rc.readBroadcastInt(ARCHON_PRIORITIES_MIN_IDX + i);
            if (currRobotPriority == 0) return false; // List of archon priorities not complete yet
            else if (currRobotPriority > highestPriority) {
                highestPriority = currRobotPriority;
                highestPriorityId = rc.readBroadcastInt(ARCHON_IDS_MIN_IDX + i);

            }
        }
        rc.broadcastInt(ARCHON_LEADER_ID_IDX, highestPriorityId);
        rc.broadcastInt(ARCHON_LEADER_STATUS_IDX, highestPriority);
        rc.broadcastInt(ARCHON_LEADER_UPDATE_IDX, rc.getRoundNum());

        return true;
    }

    /**
     * Find and broadcast the priority and id of this archon (higher number means more priority).
     *
     * @throws GameActionException
     */
    static void getArchonPriority() throws GameActionException {
        System.out.println("Getting archon priority.");

        MapLocation myLoc = rc.getLocation();

        // Determine broadcast indices
        int PRIORITY_IDX = -1;
        int ID_IDX = -1;
        for (int i = 0; i < numArchons; i++) {
            if (rc.readBroadcastInt(ARCHON_PRIORITIES_MIN_IDX + i) == 0) {
                PRIORITY_IDX = ARCHON_PRIORITIES_MIN_IDX + i;
                ID_IDX = ARCHON_IDS_MIN_IDX + i;
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

        // Archon completely surrounded and can't move
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
                // Archon relatively close to enemy archon
                boolean isClose = false;
                for (MapLocation enemyLoc : ENEMY_ARCHON_LOCS) {
                    float enemyDist = myLoc.distanceTo(enemyLoc);
                    Direction enemyDir = myLoc.directionTo(enemyLoc);
                    if (Math.abs(enemyDir.getDeltaX(enemyDist)) < 30 && Math.abs(enemyDir.getDeltaY(enemyDist)) < 30) {
                        rc.broadcastInt(PRIORITY_IDX, ArchonStatus.CLOSE.statusToNum());
                        isClose = true;
                        System.out.println("Relatively near enemy archon, priority 5.");
                        break;
                    }
                }
                if (!isClose) {
                    // Archon completely surrounded by trees but can move/build units
                    float DIST_TO_CIRCLE = RobotType.ARCHON.bodyRadius + RobotType.GARDENER.bodyRadius +
                            GameConstants.BULLET_TREE_RADIUS;
                    float CIRCLE_RADIUS = RobotType.GARDENER.bodyRadius + (GameConstants.BULLET_TREE_RADIUS * 2);
                    int clearCircles = 0;
                    for (int i = 0; i < 8; i++) {
                        MapLocation circleCenter = myLoc.add((float) (i * Math.PI / 4), DIST_TO_CIRCLE);
                        if (!rc.onTheMap(circleCenter, CIRCLE_RADIUS)) continue;
                        TreeInfo[] circleTrees = rc.senseNearbyTrees(circleCenter, CIRCLE_RADIUS, Team.NEUTRAL);
                        if (circleTrees.length == 0) {
                            clearCircles++;
                            break;
                        }
                    }
                    System.out.println("Number of clear circles: " + clearCircles);
                    if (clearCircles == 0) {
                        rc.broadcastInt(PRIORITY_IDX, ArchonStatus.SURROUNDED.statusToNum());
                        System.out.println("Surrounded by trees, priority 3.");
                    }
                    // All other situations
                    else {
                        rc.broadcastInt(PRIORITY_IDX, ArchonStatus.FREE.statusToNum());
                        System.out.println("Other, priority 4.");
                    }
                }
            }
        }

        rc.broadcastInt(ID_IDX, rc.getID());
    }

    /* ******************** END ARCHON CODE ******************** */

    /* ******************** BEGIN GARDENER CODE ******************** */

    static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        ArchonStatus leaderArchonStatus = ArchonStatus.numToStatus(rc.readBroadcastInt(ARCHON_LEADER_STATUS_IDX));
        boolean buildDisruptiveSoldier = true;
        boolean foundSpotFromBroadcast;
        boolean settled = false;
        int cnt = GARDENER_LOCS_MIN_IDX;
        int DISRUPTIVE_SOLDIER_ROUND_LIMIT = 100;
        int MAX_NEEDED_LUMBERJACKS = 0;
        int numLumberjacks = 0;
        double TREE_HEALTH_PER_LUMBERJACK = 3000.0; // Build lumberjack for every n neutral tree health
        MapLocation myLoc = rc.getLocation();
        MapLocation targetLoc;
        int numStepsForSettle = 0;

        // Find all viable spots
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
            System.out.println("Didn't find valid loc.");
            targetLoc = myLoc;
            foundSpotFromBroadcast = false;
            mLineStart = null;
            prevDir = null;
        }
        else {
            System.out.println("Found valid loc.");
            foundSpotFromBroadcast = true;
            mLineStart = myLoc;
            prevDir = null;
        }

        // Make gardener wander and build tanks if in mid/late game
        boolean buildTanks = false;
        if (rc.getRoundNum() > 1000 && leaderArchonStatus == ArchonStatus.FREE) {
            System.out.println("Tank building gardener.");
            buildTanks = true;
        }
        MapLocation prevTargetLoc = null;
        int tankCnt = 0;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                myLoc = rc.getLocation();
                leaderArchonStatus = ArchonStatus.numToStatus(rc.readBroadcastInt(ARCHON_LEADER_STATUS_IDX));

                if (buildTanks) {
                    // Wander towards enemy to go away from own trees and have to room to build
                    if (rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX) != 0) {
                        System.out.println("Going towards enemy to build tank..");
                        targetLoc = new MapLocation(rc.readBroadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1),
                                rc.readBroadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2));
                        if (prevTargetLoc == null || prevTargetLoc.equals(targetLoc)) {
                            prevTargetLoc = targetLoc;
                            prevDir = null;
                            mLineStart = myLoc;
                        }
                        bugPathing(targetLoc);
                    }
                    else {
                        tryMove(randomDirection());
                    }
                    if (rc.getTeamBullets() > TANK_THRESHOLD && turnsSinceTank > TANK_COOLDOWN) {
                        System.out.println("Try to build tank.");
                        if (tryBuildUnit(RobotType.TANK) != null) {
                            turnsSinceTank = 0;
                        }
                    }
                    turnsSinceTank++;
                    // Make sure archon doesn't build another gardener until 100 turns after this was built
                    if (tankCnt < 100) {
                        rc.broadcastInt(LAST_GARDENER_SETTLED_IDX, rc.getRoundNum());
                        tankCnt++;
                    }
                }
                else {
                    // Settle gardener into desired location
                    while (!settled) {
                        System.out.println(myLoc + " " + targetLoc + " " + myLoc.distanceTo(targetLoc));
                        // Settle gardener in place, broadcast possible neighbors, and prepare to build
                        if (targetLoc.distanceTo(myLoc) < 0.1f || numStepsForSettle >= 100) {
                            System.out.println("Settled in place.");
                            broadcastNeighbors();
                            // Only overwrite array if we found the location via broadcast
                            if (foundSpotFromBroadcast) {
                                rc.broadcastFloat(gardenerLocIdx, -1);
                                rc.broadcastFloat(gardenerLocIdx, -1);
                            }
                            settled = true;
                            rc.broadcastInt(LAST_GARDENER_SETTLED_IDX, -1);

                            // Find number of needed lumberjacks
                            float totalNeutralTreeHealth = 1; // This makes gardener always spawn at least 1 lumberjack
                            for (TreeInfo tree : rc.senseNearbyTrees(-1, Team.NEUTRAL)) {
                                totalNeutralTreeHealth += tree.health;
                            }
                            MAX_NEEDED_LUMBERJACKS = (int) Math.ceil(totalNeutralTreeHealth / TREE_HEALTH_PER_LUMBERJACK);
                            System.out.println("Need " + MAX_NEEDED_LUMBERJACKS + " lumberjacks.");
                            mLineStart = null;
                            prevDir = null;
                        }
                        // Move towards desired loc
                        else {
                            System.out.println("Moving towards desired gardener loc.");
                            // If location is occupied or off the map, find a new one
                            if (rc.canSenseAllOfCircle(targetLoc, RobotType.GARDENER.bodyRadius) &&
                                    (rc.isCircleOccupiedExceptByThisRobot(targetLoc, RobotType.GARDENER.bodyRadius) ||
                                            !rc.onTheMap(targetLoc, RobotType.GARDENER.bodyRadius))) {
                                System.out.println("Desired loc invalid, finding a new one.");
                                // Make desired location invalid
                                rc.broadcastFloat(gardenerLocIdx, -1);
                                rc.broadcastFloat(gardenerLocIdx, -1);
                                targetLoc = findNewGardenerSpot();
                                // 1st gardener or no viable gardener spots
                                if (targetLoc == null) {
                                    System.out.println("Didn't find valid loc.");
                                    targetLoc = myLoc;
                                    foundSpotFromBroadcast = false;
                                    mLineStart = null;
                                    prevDir = null;
                                }
                                else {
                                    System.out.println("Found valid loc.");
                                    foundSpotFromBroadcast = true;
                                    mLineStart = myLoc;
                                    prevDir = null;
                                }
                            }
                            if (leaderArchonStatus == ArchonStatus.FREE) {
                                tryMoveAdvanced(targetLoc, 10, 17);
                            }
                            else {
                                bugPathing(targetLoc);
                            }
                            myLoc = rc.getLocation();
                            rc.broadcastInt(LAST_GARDENER_SETTLED_IDX, rc.getRoundNum());
                            rc.setIndicatorDot(targetLoc, 255, 0, 0);
                            numStepsForSettle++; // Just settle after 100 turns
                            Clock.yield(); // Stay in loop until desired loc is reached.
                        }
                    }

                    // Build a disruptive soldier if very early in the game
                    while (buildDisruptiveSoldier) {
                        System.out.println("Trying to build disruptive soldier.");
                        // If enemy archon's close, build the disruptive soldier
                        if (rc.getRoundNum() < DISRUPTIVE_SOLDIER_ROUND_LIMIT && (leaderArchonStatus == ArchonStatus.CRAMPED ||
                                leaderArchonStatus == ArchonStatus.CLOSE)) {
                            if (tryBuildUnit(RobotType.SOLDIER) != null) {
                                System.out.println("Built disruptive soldier.");
                                buildDisruptiveSoldier = false;
                            }
                            else {
                                System.out.println("Waiting to build disruptive soldier");
                                Clock.yield();
                            }
                        }
                        else {
                            buildDisruptiveSoldier = false;
                            System.out.println("No time to build disruptive soldier, move on.");
                        }
                    }

                    // Don't build lumberjacks if enemy archon's close and most of surrounding area is clear of trees.
                    boolean buildNeededLumberjacks = true;
                    if (leaderArchonStatus == ArchonStatus.CLOSE || leaderArchonStatus == ArchonStatus.CRAMPED) {
                        TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(3, Team.NEUTRAL);
                        double totalNeutralTreeHealth = 0;
                        for (TreeInfo tree : nearbyNeutralTrees) {
                            totalNeutralTreeHealth += tree.health;
                        }
                        if (totalNeutralTreeHealth < 500) {
                            System.out.println("Don't build early lumberjacks.");
                            buildNeededLumberjacks = false;
                        }
                    }

                    // Must build the needed lumberjacks before other units.
                    while (numLumberjacks < MAX_NEEDED_LUMBERJACKS && buildNeededLumberjacks) {
                        System.out.println("Building needed lumberjacks.");
                        if (tryBuildUnit(RobotType.LUMBERJACK) != null) {
                            numLumberjacks++;
                        }
                        if (numLumberjacks == MAX_NEEDED_LUMBERJACKS) break;
                        Clock.yield();
                    }

                    soldierRush();
                }

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
     * Broadcast up to 4 possible neighbors to this gardener.
     *
     * @throws GameActionException
     */
    static void broadcastNeighbors() throws GameActionException {
        float gardenerRadius = RobotType.GARDENER.bodyRadius;
        MapLocation myLoc = rc.getLocation();
        int cnt = GARDENER_LOCS_MIN_IDX;

        for (int i = 0; i < 4; i++) {
            MapLocation checkLoc = myLoc.add((float) ( Math.PI / 2 * i),
                    RobotType.GARDENER.sensorRadius - 0.01f); // Floating point errors
            rc.setIndicatorDot(checkLoc, 0, 0, 255);
            // Found valid neighbor
            if (rc.onTheMap(checkLoc)) {
                rc.setIndicatorDot(checkLoc, 0, 0, 0);
                // If array index already has info, don't overwrite it. Find lowest free index.
                while (rc.readBroadcastFloat(cnt) > 0 || rc.readBroadcastFloat(cnt + 1) > 0) {
                    cnt += 2;
                }
                // Broadcast actual intended neighbor (which is outside sensor radius)
                float GAP_SIZE = 3.0f;
                MapLocation intendedLoc = myLoc.add((float) ( Math.PI / 2 * i),
                        2 * RobotType.GARDENER.bodyRadius + 4 * GameConstants.BULLET_TREE_RADIUS +
                                GAP_SIZE);
                rc.setIndicatorDot(intendedLoc, 0, 0, 0);
                rc.broadcastFloat(cnt, intendedLoc.x);
                rc.broadcastFloat(cnt + 1, intendedLoc.y);
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
            if (testLoc.x == -1 || testLoc.y == -1) {
                gardenerLocIdx += 2;
            }
            else {
                return testLoc;
            }
        }
        return null;
    }

    /**
     * Attempt to grow hextrees in a grid.
     * Rushes soldiers
     *
     * @throws GameActionException
     */
    static void soldierRush() throws GameActionException {
        System.out.println("Soldier rush.");

        // Plant a tree if possible (always leave a gap for building units)
        for (int i=0;i<6;i++) {
            Direction dir = new Direction((float) (i * Math.PI / 3));
            if (gap == null) {
                if (rc.canPlantTree(dir)) {
                    System.out.println("Set gap.");
                    gap = dir;
                }
            }
            // Check that direction is not the gap
            else if (!gap.equals(dir, (float) 1e-3) && rc.canPlantTree(dir)) {
                System.out.println("Planting tree.");
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
                System.out.println("Watering lowest tree.");
                rc.water(lowestTree.getID());
            }
        }

        ArchonStatus leaderArchonStatus = ArchonStatus.numToStatus(rc.readBroadcastInt(ARCHON_LEADER_STATUS_IDX));

        // If there's nearby bullets or archons, build more soldiers
        if (rc.senseNearbyBullets(-1).length > 0 || leaderArchonStatus == ArchonStatus.CRAMPED ||
                leaderArchonStatus == ArchonStatus.CLOSE) {
            System.out.println("Increased soldier output");
            SOLDIER_COOLDOWN = 30;
            SOLDIER_THRESHOLD = RobotType.SOLDIER.bulletCost * 1.4f;
        }
        else {
            System.out.println("Regular soldier output");
            SOLDIER_COOLDOWN = 50;
            SOLDIER_THRESHOLD = RobotType.SOLDIER.bulletCost * 1.5f;
        }

        System.out.println(turnsSinceSoldier + " " + SOLDIER_COOLDOWN);
        System.out.println(rc.getTeamBullets() + " " + SOLDIER_THRESHOLD);
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

        ArrayList<MapLocation> initialNeutralTreesLocs = new ArrayList<>();
        for (TreeInfo tree : rc.senseNearbyTrees(-1, Team.NEUTRAL)) {
            initialNeutralTreesLocs.add(tree.getLocation());
        }
        RobotInfo targetRobot = null;
        MapLocation targetTreeLoc = null;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                // Strike nearby enemy units
                RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(-1, ENEMY);
                if (nearbyEnemyRobots.length > 0) {
                    System.out.println("Sensed enemy units.");
                    RobotInfo nearbyEnemy = nearbyEnemyRobots[0];
                    MapLocation nearbyEnemyLoc = nearbyEnemy.location;
                    rc.setIndicatorDot(nearbyEnemyLoc, 255, 0, 0);
                    if (targetRobot == null || !targetRobot.equals(nearbyEnemy)) {
                        targetRobot = nearbyEnemy;
                        targetTreeLoc = null;
                        prevDir = null;
                        mLineStart = rc.getLocation();
                    }
                    bugPathing(nearbyEnemyLoc);
                    if (MapLocation.doCirclesCollide(rc.getLocation(), GameConstants.LUMBERJACK_STRIKE_RADIUS,
                            nearbyEnemyLoc, nearbyEnemy.getRadius()) && rc.canStrike()) {
                        rc.strike();
                        System.out.println("Strike enemy unit.");
                    }
                }
                // Chop a tree
                else {
                    System.out.println("Looking to chop a tree.");
                    TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);

                    if (nearbyNeutralTrees.length > 0) {
                        System.out.println("Found nearby neutral trees.");
                        // Chop initial trees before others
                        if (initialNeutralTreesLocs.size() > 0) {
                            System.out.println("Matching with initial neutral trees.");
                            int cnt = 0;
                            MapLocation treeLoc = nearbyNeutralTrees[cnt].location;
                            while (!initialNeutralTreesLocs.contains(treeLoc)) {
                                cnt++;
                                if (cnt == nearbyNeutralTrees.length) break;
                                treeLoc = nearbyNeutralTrees[cnt].location;
                            }
                            if (cnt == nearbyNeutralTrees.length) {
                                System.out.println("Couldn't match tree, taking first initial tree.");
                                treeLoc = initialNeutralTreesLocs.get(0);
                            }
                            else {
                                System.out.println("Matched a tree.");
                            }
                            System.out.println(treeLoc);
                            rc.setIndicatorDot(treeLoc, 255, 0, 0);

                            if (targetTreeLoc == null || !targetTreeLoc.equals(treeLoc)) {
                                targetRobot = null;
                                targetTreeLoc = treeLoc;
                                prevDir = null;
                                mLineStart = rc.getLocation();
                            }

                            if (rc.canInteractWithTree(treeLoc)) {
                                System.out.println("Chopping the tree.");
                                rc.chop(treeLoc);
                                if (rc.canSenseLocation(treeLoc) && rc.senseTreeAtLocation(treeLoc) == null) {
                                    System.out.println("Tree dead, removing from list.");
                                    initialNeutralTreesLocs.remove(treeLoc);
                                }
                            }
                            else {
                                System.out.println("Moving to the tree.");
                                bugPathing(treeLoc);
                            }
                        }
                        // Chop random tree
                        else {
                            System.out.println("Chopping random tree.");
                            TreeInfo tree = nearbyNeutralTrees[0];

                            if (targetTreeLoc == null || !targetTreeLoc.equals(tree.location)) {
                                targetRobot = null;
                                targetTreeLoc = tree.location;
                                prevDir = null;
                                mLineStart = rc.getLocation();
                            }

                            if (rc.canChop(tree.ID)) {
                                rc.chop(tree.ID);
                            }
                            else {
                                bugPathing(tree.location);
                            }
                        }
                    }
                    else {
                        System.out.println("Nothing to do, move randomly.");
                        tryMove(randomDirection());
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
                checkDonate();

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
                    if (currentArchonIndex < numArchons) {
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
                        if (!dodge()) {
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
                    if (!dodge()) {
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

    /* ******************** BEGIN SOLDIER CODE ******************** */

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");

        RobotInfo target;
        MapLocation targetLoc;
        MapLocation prevTargetLoc = null;
        int currentArchonIndex = 0; // Current enemy archon we are tracking

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // This loop is solely so body can exit early if wanted.
            for (int xyz = 0; xyz < 1; xyz++) {
                // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
                try {

                    // Check if team can win via Victory Points
                    checkDonate();

                    target = null;
                    targetLoc = null;

                    MapLocation myLoc = rc.getLocation();

                    // Search for nearby enemy robots
                    RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(-1, ENEMY);
                    // Priority is gardener > archon > others > tank, then distance for same RobotType
                    if (nearbyEnemyRobots.length > 0) {
                        // The following variables are initialized to give least priority
                        RobotType targetType = null;
                        float targetDist = rc.getType().sensorRadius + 1;
                        int targetPriority = getSoldierTargetPriority(targetType);
                        // Find target with most priority
                        for (RobotInfo rb : nearbyEnemyRobots) {
                            RobotType rbType = rb.type;
                            int rbPriority = getSoldierTargetPriority(rbType);
                            if (rbPriority == -1) continue; // Ignore unit
                            if (rbPriority > targetPriority || (rbPriority == targetPriority &&
                                    myLoc.distanceTo(rb.location) < targetDist)) {
                                target = rb;
                                targetLoc = target.location;
                                targetType = target.type;
                                targetDist = targetLoc.distanceTo(myLoc);
                                targetPriority = getSoldierTargetPriority(targetType);
                            }
                        }
                        if (target != null) {
                            System.out.println("Found nearby target.");
                            if (prevTargetLoc == null || !prevTargetLoc.equals(targetLoc)) {
                                System.out.println("Switch targets.");
                                prevTargetLoc = targetLoc;
                                prevDir = null;
                                mLineStart = myLoc;
                            }
                            if (rc.readBroadcastFloat(SOLDIER_MIN_BROADCAST_IDX) == 0 ||
                                    rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX) == rc.getID()) {
                                rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX, rc.getID());
                                rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1, targetLoc.x);
                                rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2, targetLoc.y);
                                rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX + 3, rc.getRoundNum());
                            }
                        }
                    }

                    if (target == null) {
                        // If leader soldier died, erase its data
                        if (rc.getRoundNum() - rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX + 3) > 2) {
                            rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX, 0);
                            rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1, 0);
                            rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2, 0);
                            rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX + 3, 0);
                        }
                        // Check if there's a target to follow
                        if (rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX) != 0) {
                            System.out.println("Going to attack known enemy.");
                            targetLoc = new MapLocation(rc.readBroadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1),
                                    rc.readBroadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2));
                            if (prevTargetLoc == null || prevTargetLoc.equals(targetLoc)) {
                                prevTargetLoc = targetLoc;
                                prevDir = null;
                                mLineStart = myLoc;
                            }
                        }
                        // Couldn't find nearby robots, find the initial position of a enemy archon
                        else if (currentArchonIndex < numArchons) {
                            System.out.println("Find location of enemy archon.");
                            targetLoc = ENEMY_ARCHON_LOCS[currentArchonIndex];

                            if (prevTargetLoc == null || !prevTargetLoc.equals(targetLoc)) {
                                prevTargetLoc = targetLoc;
                                prevDir = null;
                                mLineStart = myLoc;
                            }
                        }
                        // No enemy archons to track anymore, random location
                        else {
                            System.out.println("Pick random location.");
                            targetLoc = myLoc.add(randomDirection(), rc.getType().strideRadius);
                            prevTargetLoc = null;
                            prevDir = null;
                            mLineStart = null;
                            tryMove(randomDirection());
                            tryShake();
                            Clock.yield();
                            break; // Restart from beginning of while loop
                        }
                    }
                    rc.setIndicatorDot(targetLoc, 0, 255, 0);

                    // Prepare to attack if target loc is in sight (and we are not tracking based on old archon data)
                    if (rc.canSenseLocation(targetLoc) && target != null) {
                        System.out.println("Target loc in range.");

                        // Move towards target
                        if (!dodge()) {
                            if (!bugPathing(targetLoc)) {
                                tryMove(randomDirection());
                            }
                        }
                        myLoc = rc.getLocation();

                        // Shoot at target
                        System.out.println("Shooting at target.");
                        Direction dirToTarget = myLoc.directionTo(targetLoc);
                        RobotType targetType = target.getType();
                        if (rc.canFirePentadShot() && (targetType == RobotType.SOLDIER || targetType == RobotType.TANK)) {
                            rc.firePentadShot(dirToTarget);
                        }
                        else if (rc.canFireTriadShot() && targetType != RobotType.ARCHON) {
                            rc.fireTriadShot(dirToTarget);
                        }
                        else if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(dirToTarget);
                        }
                    }
                    // Else dodge bullets or move towards the target
                    else {
                        System.out.println("Target not in range, moving to it.");
                        if (!dodge()) {
                            bugPathing(targetLoc);
                        }
                        else {
                            System.out.println("Dodging bullets instead");
                        }
                        myLoc = rc.getLocation();

                        // If nothing's here, tracked robot is dead
                        if (rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX) == rc.getID() &&
                                myLoc.isWithinDistance(targetLoc, rc.getType().sensorRadius / 2)) {
                            rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX, 0);
                            rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1, 0);
                            rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2, 0);
                        }

                        // If nothing's there, we were tracking an archon that already left or random loc
                        // In that case, increment the tracked archon
                        else if (myLoc.isWithinDistance(targetLoc, rc.getType().sensorRadius / 2)) {
                            currentArchonIndex += 1;
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
    }

    /**
     * Get the priority of targets for the soldier.
     *
     * @param rt The type of robot in question
     * @return The priority of that robot. A higher number means higher priority. -1 means ignore.
     */
    static int getSoldierTargetPriority(RobotType rt) {
        if (rt == null) return -1;
        switch (rt) {
            case ARCHON:
                if (rc.getRoundNum() < 200) return -1;
                else return 1;
            case GARDENER: return 6;
            case LUMBERJACK: return 4;
            case SOLDIER: return 5;
            case SCOUT: return 2;
            case TANK: return 3;
        }
        return -1;
    }

    /* ******************** END SOLDIER CODE ******************** */

    /* ******************** BEGIN TANK CODE ******************** */

    // TODO: Add tank functionality
    static void runTank() throws GameActionException {
        System.out.println("I'm a tank!");

        RobotInfo target;
        MapLocation targetLoc;
        MapLocation prevTargetLoc = null;
        int currentArchonIndex = 0; // Current enemy archon we are tracking

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // This loop is solely so body can exit early if wanted.
            for (int xyz = 0; xyz < 1; xyz++) {
                // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
                try {

                    // Check if team can win via Victory Points
                    checkDonate();

                    target = null;
                    targetLoc = null;

                    // Record location (obviously)
                    MapLocation myLoc = rc.getLocation();

                    // Search for nearby enemy robots
                    RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(-1, ENEMY);
                    // Priority is gardener > archon > others > tank, then distance for same RobotType
                    if (nearbyEnemyRobots.length > 0) {
                        // The following variables are initialized to give least priority
                        RobotType targetType = null;
                        float targetDist = rc.getType().sensorRadius + 1;
                        int targetPriority = getTankTargetPriority(targetType);
                        // Find target with most priority
                        for (RobotInfo rb : nearbyEnemyRobots) {
                            RobotType rbType = rb.type;
                            int rbPriority = getTankTargetPriority(rbType);
                            if (rbPriority == -1) continue; // Ignore unit
                            if (rbPriority > targetPriority || (rbPriority == targetPriority &&
                                    myLoc.distanceTo(rb.location) < targetDist)) {
                                target = rb;
                                targetLoc = target.location;
                                targetType = target.type;
                                targetDist = targetLoc.distanceTo(myLoc);
                                targetPriority = getTankTargetPriority(targetType);
                            }
                        }
                        if (target != null) {
                            System.out.println("Found nearby target.");
                            if (prevTargetLoc == null || !prevTargetLoc.equals(targetLoc)) {
                                System.out.println("Switch targets.");
                                prevTargetLoc = targetLoc;
                                prevDir = null;
                                mLineStart = myLoc;
                            }
                            if (rc.readBroadcastFloat(SOLDIER_MIN_BROADCAST_IDX) == 0 ||
                                    rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX) == rc.getID()) {
                                rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX, rc.getID());
                                rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1, targetLoc.x);
                                rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2, targetLoc.y);
                                rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX + 3, rc.getRoundNum());
                            }
                        }
                    }

                    if (target == null) {
                        // If leader soldier died, erase its data
                        if (rc.getRoundNum() - rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX + 3) > 2) {
                            rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX, 0);
                            rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1, 0);
                            rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2, 0);
                            rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX + 3, 0);
                        }

                        // Check if there's a target to follow
                        if (rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX) != 0) {
                            System.out.println("Going to attack known enemy.");
                            targetLoc = new MapLocation(rc.readBroadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1),
                                    rc.readBroadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2));
                            if (prevTargetLoc == null || prevTargetLoc.equals(targetLoc)) {
                                prevTargetLoc = targetLoc;
                                prevDir = null;
                                mLineStart = myLoc;
                            }
                        }
                        // Couldn't find nearby robots, find the initial position of a enemy archon
                        else if (currentArchonIndex < numArchons) {
                            System.out.println("Find location of enemy archon.");
                            targetLoc = ENEMY_ARCHON_LOCS[currentArchonIndex];

                            if (prevTargetLoc == null || !prevTargetLoc.equals(targetLoc)) {
                                prevTargetLoc = targetLoc;
                                prevDir = null;
                                mLineStart = myLoc;
                            }
                        }
                        // No enemy archons to track anymore, random location
                        else {
                            System.out.println("Pick random location.");
                            targetLoc = myLoc.add(randomDirection(), rc.getType().strideRadius);
                            prevTargetLoc = null;
                            prevDir = null;
                            mLineStart = null;
                            tryMove(randomDirection());
                            tryShake();
                            Clock.yield();
                            break; // Restart from beginning of while loop
                        }
                    }
                    rc.setIndicatorDot(targetLoc, 0, 255, 0);

                    // Prepare to attack if target loc is in sight (and we are not tracking based on old archon data)
                    if (rc.canSenseLocation(targetLoc) && target != null) {
                        System.out.println("Target loc in range.");

                        // Move towards target
                        if (!dodge()) {
                            // FIXME: Decide between tryMoveAdvanced and bugPathing
                            if (!bugPathing(targetLoc)) {
                                tryMove(randomDirection());
                            }
                        }
                        myLoc = rc.getLocation();

                        // Shoot at target
                        System.out.println("Shooting at target.");
                        Direction dirToTarget = myLoc.directionTo(targetLoc);
                        RobotType targetType = target.getType();
                        if (rc.canFirePentadShot() && (targetType == RobotType.SOLDIER || targetType == RobotType.TANK)) {
                            rc.firePentadShot(dirToTarget);
                        }
                        else if (rc.canFireTriadShot() && targetType != RobotType.ARCHON) {
                            rc.fireTriadShot(dirToTarget);
                        }
                        else if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(dirToTarget);
                        }
                    }
                    // Else dodge bullets or move towards the target
                    else {
                        System.out.println("Target not in range, moving to it.");
                        bugPathing(targetLoc);
                        myLoc = rc.getLocation();

                        // If nothing's here, tracked robot is dead
                        if (rc.readBroadcastInt(SOLDIER_MIN_BROADCAST_IDX) == rc.getID() &&
                                myLoc.isWithinDistance(targetLoc, rc.getType().sensorRadius / 2)) {
                            rc.broadcastInt(SOLDIER_MIN_BROADCAST_IDX, 0);
                            rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 1, 0);
                            rc.broadcastFloat(SOLDIER_MIN_BROADCAST_IDX + 2, 0);
                        }

                        // If nothing's there, we were tracking an archon that already left or random loc
                        // In that case, increment the tracked archon
                        else if (myLoc.isWithinDistance(targetLoc, rc.getType().sensorRadius / 2)) {
                            currentArchonIndex += 1;
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
    }

    /**
     * Get the priority of targets for the tank.
     *
     * @param rt The type of robot in question
     * @return The priority of that robot. A higher number means higher priority. -1 means ignore.
     */
    static int getTankTargetPriority(RobotType rt) {
        if (rt == null) return -1;
        switch (rt) {
            case ARCHON: return 2;
            case GARDENER: return 6;
            case LUMBERJACK: return 3;
            case SOLDIER: return 4;
            case SCOUT: return 1;
            case TANK: return 5;
        }
        return -1;
    }

    /* ******************** END TANK CODE ******************** */

    /* ******************** Below are extra methods that the robots above use. ******************** */

    /**
     * Implement bug pathing algorithm that follows the m-line between starting and ending locations
     * It follows obstacles when it encounters them.
     *
     * @param loc The desired location.
     * @return true if the robot moved
     * @throws GameActionException
     */
    static boolean bugPathing(MapLocation loc) throws GameActionException {
        System.out.println("Begin bug pathing.");
        int first = Clock.getBytecodeNum();
        MapLocation myLoc = rc.getLocation();

        // Check if can move directly to location
        if (myLoc.distanceTo(loc) < rc.getType().strideRadius * 2 && rc.canMove(loc)) {
            rc.move(loc);
            int second = Clock.getBytecodeNum();
            System.out.println(second - first);
            return true;
        }
        rc.setIndicatorLine(mLineStart, loc, 0, 0, 0);
        // Check if can follow m-line
        Direction mLineDir = mLineStart.directionTo(loc);
        mLineDir = new Direction((float) (Math.round(mLineDir.getAngleDegrees()) * Math.PI / 180));
        double angleBetween = myLoc.equals(mLineStart) ? 0 : mLineDir.radiansBetween(myLoc.directionTo(mLineStart));
        double distToMLine = myLoc.distanceTo(mLineStart) * Math.sin(Math.abs(angleBetween));
        System.out.println("m-line dir: " + mLineDir.getAngleDegrees());
        // On m-line, move in its direction
        if (distToMLine < rc.getType().bodyRadius / 2) {
            System.out.println("Follow m-line.");
            if (rc.canMove(mLineDir, rc.getType().strideRadius)) {
                rc.move(mLineDir, rc.getType().strideRadius);
                prevDir = null;
                int second = Clock.getBytecodeNum();
                System.out.println(second - first);
                return true;
            }
            else if (rc.canMove(mLineDir, rc.getType().strideRadius / 2)) {
                rc.move(mLineDir, rc.getType().strideRadius / 2);
                prevDir = null;
                int second = Clock.getBytecodeNum();
                System.out.println(second - first);
                return true;
            }
            else if (rc.canMove(mLineDir, rc.getType().strideRadius / 4)) {
                rc.move(mLineDir, rc.getType().strideRadius / 4);
                prevDir = null;
                int second = Clock.getBytecodeNum();
                System.out.println(second - first);
                return true;
            }
            System.out.println("Couldn't follow m-line.");
        }

        if (prevDir != null) {
            System.out.println("Prev dir before: " + prevDir.getAngleDegrees());
        }
        else {
            System.out.println("Prev dir before: null");
        }
        // Just encountered obstacle
        if (prevDir == null || !rc.canMove(prevDir)) {
            System.out.println("Just encountered obstacle.");
            Direction useDir = prevDir == null ? mLineDir : prevDir;
            for (int i = 1; i <= 180; i++) {
                Direction testDir = useDir.rotateLeftDegrees(i);
                if (rc.canMove(testDir)) {
                    prevDir = testDir;
                    break;
                }
                testDir = useDir.rotateRightDegrees(i);
                if (rc.canMove(testDir)) {
                    prevDir = testDir;
                    break;
                }
            }
            if (prevDir == null) {
                System.out.println("Couldn't find way around obstacle.");
                int second = Clock.getBytecodeNum();
                System.out.println(second - first);
                return false;
            }
        }
        rc.setIndicatorLine(myLoc, myLoc.add(prevDir, 2), 0, 0, 255);
        System.out.println("Prev dir after: " + prevDir.getAngleDegrees());

        // Follow obstacle
        System.out.println("Follow obstacle.");
        Direction mostLeftDir = prevDir;
        Direction mostRightDir = prevDir;
        boolean reachedMostLeftDir = false;
        boolean reachedMostRightDir = false;
        for (int i = 0; i <= 180; i++) {
            if (!reachedMostLeftDir) {
                Direction testDir = prevDir.rotateLeftDegrees(i);
                if (rc.canMove(testDir)) {
                    mostLeftDir = testDir;
                }
                else {
                    reachedMostLeftDir = true;
                }
            }
            if (!reachedMostRightDir) {
                Direction testDir = prevDir.rotateRightDegrees(i);
                if (rc.canMove(testDir)) {
                    mostRightDir = testDir;
                }
                else {
                    reachedMostRightDir = true;
                }
            }
            if (reachedMostLeftDir && reachedMostRightDir) break;
        }
        // Can move in any direction, move towards target. Prevents wiggling in open spaces.
        if (mostLeftDir.equals(prevDir.opposite(), (float) 1e-3) && mostRightDir.equals(prevDir.opposite(), (float) 1e-3)) {
            System.out.println("Completely free to move, moving towards target.");
            return tryMove(loc);
        }
        else if (Math.abs(mostLeftDir.degreesBetween(prevDir)) < Math.abs(mostRightDir.degreesBetween(prevDir)) &&
                rc.canMove(mostLeftDir)) {
            rc.setIndicatorLine(myLoc, myLoc.add(mostLeftDir, rc.getType().strideRadius), 0, 255, 0);
            rc.setIndicatorLine(myLoc, myLoc.add(mostRightDir, rc.getType().strideRadius), 255, 0, 0);
            rc.move(mostLeftDir);
            prevDir = mostLeftDir;
            System.out.println("Moving left.");
            System.out.println(prevDir.getAngleDegrees());
            int second = Clock.getBytecodeNum();
            System.out.println(second - first);
            return true;
        }
        else if (rc.canMove(mostRightDir)) {
            rc.setIndicatorLine(myLoc, myLoc.add(mostLeftDir, rc.getType().strideRadius), 255, 0, 0);
            rc.setIndicatorLine(myLoc, myLoc.add(mostRightDir, rc.getType().strideRadius), 0, 255, 0);
            rc.move(mostRightDir);
            prevDir = mostRightDir;
            System.out.println("Moving right.");
            System.out.println(prevDir.getAngleDegrees());
            int second = Clock.getBytecodeNum();
            System.out.println(second - first);
            return true;
        }
        System.out.println("Can't bug path.");
        return false;
    }

    /**
     * Robot donates bullets if there are extra.
     * @throws GameActionException
     */
    static void checkDonate() throws GameActionException {
        float BULLET_THRESHOLD = 1500;
        int ROUND_THRESHOLD = 200;
        // Donate all bullets if it gives >1000 VPs or if it's the last round
        if (rc.getTeamBullets() > (GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints()) *
                rc.getVictoryPointCost() || rc.getRoundNum() == rc.getRoundLimit() - 1) {
            rc.donate(rc.getTeamBullets());
        }
        else if (rc.getTeamBullets() - BULLET_THRESHOLD > rc.getVictoryPointCost() &&
                rc.getRoundNum() > ROUND_THRESHOLD) {
            int numVPEarned = (int) ((rc.getTeamBullets() - BULLET_THRESHOLD) / rc.getVictoryPointCost());
            rc.donate(numVPEarned * rc.getVictoryPointCost());
        }
    }

    /**
     * Attempt to dodge nearby bullets and lumberjacks
     *
     * @return true if there are bullets or lumberjacks nearby to dodge
     * @throws GameActionException
     */
    static boolean dodge() throws GameActionException {
        System.out.println("Dodging bullets.");
        int first = Clock.getBytecodeNum();
        MapLocation myLoc = rc.getLocation();
        MapLocation intendedLoc = rc.getLocation();
        float MAX_SENSE_RANGE = rc.getType().bodyRadius + rc.getType().strideRadius + RobotType.TANK.bulletSpeed;
        BulletInfo[] nearbyBullets = rc.senseNearbyBullets(MAX_SENSE_RANGE);
        if (nearbyBullets.length > 0) {
            for (BulletInfo bullet : nearbyBullets) {
                Direction bulletDir = bullet.dir;
                MapLocation p1 = bullet.location;
                MapLocation p2 = p1.add(bulletDir, bullet.speed);
                float xDiff = p2.x - p1.x;
                float yDiff = p2.y - p1.y;

                // Calculate smallest vector between intended loc and bullet trajectory
                float distance = (float) (Math.abs((yDiff * intendedLoc.x) - (xDiff * intendedLoc.y) + (p2.x * p1.y) - (p2.y * p1.x)) /
                        Math.sqrt((yDiff * yDiff) + (xDiff * xDiff)));
                Direction dir = (bulletDir.degreesBetween(p1.directionTo(intendedLoc)) > 0) ?
                        bulletDir.rotateLeftDegrees(90) : bulletDir.rotateRightDegrees(90);
                distance = Math.max(0, rc.getType().bodyRadius - distance);
                intendedLoc = intendedLoc.add(dir, distance);
            }
        }

        // Avoid lumberjacks
        RobotInfo[] nearbyEnemyRobots = rc.senseNearbyRobots(-1, ENEMY);
        if (nearbyEnemyRobots.length > 0) {
            for (RobotInfo enemyRobot : nearbyEnemyRobots) {
                if (enemyRobot.getType() == RobotType.LUMBERJACK && MapLocation.doCirclesCollide(intendedLoc,
                        rc.getType().bodyRadius, enemyRobot.location, GameConstants.LUMBERJACK_STRIKE_RADIUS)) {
                    float distance = rc.getType().bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS -
                            intendedLoc.distanceTo(enemyRobot.location);
                    intendedLoc = intendedLoc.add(intendedLoc.directionTo(enemyRobot.location).opposite(),
                            distance * 2);
                }
            }
        }

        // Only move if there's actually stuff to dodge.
        if (!intendedLoc.equals(myLoc)) {
            rc.setIndicatorLine(myLoc, intendedLoc, 0, 0, 0);
            rc.setIndicatorDot(myLoc, 255, 0, 0);
            return tryMove(intendedLoc, 10, 17);
        }
        int second = Clock.getBytecodeNum();
        System.out.println(second - first);
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
     * Try to build this unit in direction of enemy archon (to avoid trapping the built units in corners)
     *
     * @param rbType The type of robot we're trying to build
     * @return The RobotInfo of the robot built, or null if no robot was built
     * @throws GameActionException
     */
    static RobotInfo tryBuildUnit(RobotType rbType) throws GameActionException {
        float referenceDegrees = ((int) (rc.getLocation().directionTo(ENEMY_ARCHON_LOCS[0]).getAngleDegrees() / 5)) * 5;
        System.out.println("Reference degrees: " + referenceDegrees);
        Direction referenceDir = new Direction((float) (referenceDegrees * Math.PI / 180));

        for (int i = 0; i < 36; i++) {
            Direction dir = referenceDir.rotateLeftDegrees(i * 5);
            if (rc.canBuildRobot(rbType, dir)) {
                rc.buildRobot(rbType, dir);
                return rc.senseRobotAtLocation(rc.getLocation().add(dir, rc.getType().bodyRadius +
                        GameConstants.GENERAL_SPAWN_OFFSET + rbType.bodyRadius));
            }
            dir = referenceDir.rotateRightDegrees(i * 5);
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
        System.out.println("Begin advanced move.");

        MapLocation myLoc = rc.getLocation();
        Direction dir = myLoc.directionTo(loc);
        float distance = Math.min(rc.getLocation().distanceTo(loc), rc.getType().strideRadius);
        float bufferDist = RobotType.GARDENER.bodyRadius + 2 * GameConstants.BULLET_TREE_RADIUS + rc.getType().bodyRadius;

        RobotInfo[] nearbyTeamRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        ArrayList<RobotInfo> nearbyGardenersArrayList = new ArrayList<>();
        for (RobotInfo robot : nearbyTeamRobots) {
            if (robot.type == RobotType.GARDENER) {
                nearbyGardenersArrayList.add(robot);
            }
        }
        RobotInfo[] nearbyGardeners = nearbyGardenersArrayList.toArray(new RobotInfo[0]);
        if (nearbyGardeners.length > 0) {
            for (RobotInfo robot : nearbyGardeners) {
                if (myLoc.distanceTo(robot.location) < bufferDist) {
                    System.out.println("Moving away from gardener.");
                    Direction newDir = robot.location.directionTo(myLoc);
                    return tryMove(newDir);
                }
            }
        }
        rc.setIndicatorLine(myLoc, loc, 0, 0, 0);

        // Now try a bunch of angles
        int currentCheck = 0;

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
