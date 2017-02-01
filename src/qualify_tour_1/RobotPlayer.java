package qualify_tour_1;
import battlecode.common.*;
import java.util.*;

@SuppressWarnings({"JavaDoc", "InfiniteLoopStatement", "WeakerAccess"})
public strictfp class RobotPlayer {
    static RobotController rc;

    // Variables that can be set at beginning of run() method
    static int numArchons;
    static MapLocation[] ENEMY_ARCHON_LOCS;
    static Team ENEMY;
    static Team FRIENDLY;

    // Broadcast index limits
    static int ARCHON_MIN_BROADCAST_IDX = 0;
    static int ARCHON_MAX_BROADCAST_IDX = 99;

    // Archon variables
    enum ArchonPhase {
        ERROR (-1), FIND_INIT_LEADER(1), FIND_NEW_LEADER(2), FLEE (3), BUILD (4);

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
    static int ARCHON_PHASE_IDX; // Index of global archon phase (doesn't apply to flee)
    static int ARCHON_LEADER_ID_IDX; // Index of leader archon's id
    static int ARCHON_LEADER_STATUS_IDX; // Status of leader archon
    static int ARCHON_LEADER_UPDATE_IDX; // Broadcast round num every n rounds to know leader is still alive
    static int ARCHON_LEADER_UPDATE_THRESHOLD = 5;
    static int ARCHON_PRIORITIES_MIN_IDX; // Min index of array of archon priorities
    static int ARCHON_IDS_MIN_IDX; // Min index of array of archon ids
    static int BUILD_GARDENER_IDX; // Index of boolean which says if new gardener is needed
    static int BUILD_GARDENER_COOLDOWN = 50; // Only build gardener every n turns
    static float BUILD_GARDENER_THRESHOLD = RobotType.GARDENER.bulletCost * 1.5f; // Only build gardener when team has >n bullets
    static int turnsSinceGardener = BUILD_GARDENER_COOLDOWN + 1; // Turns since gardener was built

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        // Initialize certain variables
        FRIENDLY = rc.getTeam();
        ENEMY = FRIENDLY.opponent();
        ENEMY_ARCHON_LOCS = rc.getInitialArchonLocations(ENEMY);
        numArchons = ENEMY_ARCHON_LOCS.length;

        // Initialize archon variables
        ARCHON_PHASE_IDX = ARCHON_MIN_BROADCAST_IDX;
        ARCHON_LEADER_ID_IDX = ARCHON_PHASE_IDX + 1;
        ARCHON_LEADER_STATUS_IDX = ARCHON_LEADER_ID_IDX + 1;
        ARCHON_LEADER_UPDATE_IDX = ARCHON_LEADER_STATUS_IDX + 1;
        BUILD_GARDENER_IDX = ARCHON_LEADER_UPDATE_IDX + 1;

        // Initialize archon array variables at end
        ARCHON_PRIORITIES_MIN_IDX = ARCHON_MAX_BROADCAST_IDX - numArchons + 1;
        ARCHON_IDS_MIN_IDX = ARCHON_PRIORITIES_MIN_IDX - numArchons;

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

        ArchonPhase currPhase;
        int archonLeaderId;
        int currRoundNum;
        int myId = rc.getID();

        rc.broadcastInt(ARCHON_PHASE_IDX, ArchonPhase.FIND_INIT_LEADER.phaseToNum());
        rc.broadcastBoolean(BUILD_GARDENER_IDX, true);

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
                        }
                        break;
                    case FIND_NEW_LEADER:
                        System.out.println("Phase: Find new leader");
                        getArchonPriority();
                        if (findArchonLeader()) {
                            System.out.println("Found new leader.");
                            rc.broadcastInt(ARCHON_PHASE_IDX, ArchonPhase.BUILD.phaseToNum());
                        }
                        else {
                            numArchons -= 1;
                        }
                        break;
                    case BUILD:
                        // Only build if this archon is the leader
                        if (myId == archonLeaderId) {
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
     * Try to build a gardener.
     *
     * @return true if gardener was built
     * @throws GameActionException
     */
    // TODO: Find a way to make archon not trap itself when building gardeners
    static boolean archonBuild() throws GameActionException {
        boolean buildGardener = rc.readBroadcastBoolean(BUILD_GARDENER_IDX);
        if (buildGardener && turnsSinceGardener > BUILD_GARDENER_COOLDOWN &&
                rc.getTeamBullets() > BUILD_GARDENER_THRESHOLD) {
            if (tryBuildUnit(RobotType.GARDENER) != null) {
                turnsSinceGardener = 0;
                rc.broadcastBoolean(BUILD_GARDENER_IDX, false);
                return true;
            }
        }
        turnsSinceGardener++;
        return false;
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
                    if (circleTrees.length == 0) clearCircles++;
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

    static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        float myBodyRadius = rc.getType().bodyRadius;
        int MAX_TREES = 3;
        int numTrees = 0;
        boolean reachedTreeLimit = false;
        MapLocation myLoc;

        int MAX_NEEDED_LUMBERJACKS = 0;
        int numLumberjacks = 0;
        double TREE_HEALTH_PER_LUMBERJACK = 2000.0; // Build lumberjack for every n neutral tree health
        for (TreeInfo tree : rc.senseNearbyTrees(-1, Team.NEUTRAL)) {
            MAX_NEEDED_LUMBERJACKS += tree.health;
        }
        MAX_NEEDED_LUMBERJACKS = (int) Math.ceil(MAX_NEEDED_LUMBERJACKS / TREE_HEALTH_PER_LUMBERJACK);

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                myLoc = rc.getLocation();

                // Only build lumberjacks until enough are built
                while (numLumberjacks < MAX_NEEDED_LUMBERJACKS) {
                    System.out.println("Building needed lumberjacks.");
                    if (tryBuildUnit(RobotType.LUMBERJACK) != null) {
                        System.out.println("Built lumberjack.");
                        numLumberjacks++;
                    }
                    if (numLumberjacks == MAX_NEEDED_LUMBERJACKS) break;
                    Clock.yield();
                }

                // Plant a tree in a semi-grid formation
                if (numTrees < MAX_TREES) {
                    TreeInfo[] nearbyFriendlyTrees = rc.senseNearbyTrees(-1, FRIENDLY);
                    for (int i=0;i<12;i++) {
                        Direction dir = new Direction((float) (i * Math.PI / 6));
                        // Check that units can move between desired tree and other trees
                        if (rc.canPlantTree(dir)) {
                            boolean away = true;
                            MapLocation desiredTreeLoc = myLoc.add(dir, myBodyRadius + GameConstants.GENERAL_SPAWN_OFFSET);
                            for (TreeInfo tree : nearbyFriendlyTrees) {
                                if (tree.location.distanceTo(desiredTreeLoc) < myBodyRadius * 2 +
                                        GameConstants.BULLET_TREE_RADIUS * 2) {
                                    away = false;
                                    break;
                                }
                            }
                            if (away) {
                                rc.plantTree(dir);
                                numTrees++;
                                if (numTrees == MAX_TREES && !reachedTreeLimit) {
                                    rc.broadcastBoolean(BUILD_GARDENER_IDX, true);
                                    reachedTreeLimit = true;
                                }
                            }
                        }
                    }
                }

                // Water lowest tree
                TreeInfo[] teamTrees = rc.senseNearbyTrees(-1, FRIENDLY);
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
                    else {
                        tryMove(lowestTree.location);
                    }
                }

                // Randomly move if haven't moved yet
                if (!rc.hasMoved()) {
                    tryMove(randomDirection());
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

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                tryMove(randomDirection());

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

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                tryMove(randomDirection());

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

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                tryMove(randomDirection());

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

    static void runTank() throws GameActionException {
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
                checkDonate();

                tryMove(randomDirection());

                // Shake a tree if possible
                tryShake();

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
     * Check if it's possible to build units from this location
     *
     * @param loc The location to test
     * @return true if a unit can be built from this location
     * @throws GameActionException
     */
    static boolean canBuildUnit(MapLocation loc) throws GameActionException {
        // Check every 30 degrees
        for (int i = 0; i < 24; i++) {
            Direction dir = new Direction((float) (i * Math.PI / 12));
            if (rc.canBuildRobot(RobotType.SCOUT, dir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Robot donates bullets if there are extra.
     * @throws GameActionException
     */
    static void checkDonate() throws GameActionException {
        float BULLET_THRESHOLD = 1000;
        int ROUND_THRESHOLD = 200;
        // Donate all bullets if it gives >1000 VPs or if it's the last round
        if (rc.getTeamBullets() > (GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints()) *
                rc.getVictoryPointCost() || rc.getRoundNum() == rc.getRoundLimit()) {
            rc.donate(rc.getTeamBullets());
        }
        else if (rc.getTeamBullets() - BULLET_THRESHOLD > rc.getVictoryPointCost() &&
                rc.getRoundNum() > ROUND_THRESHOLD) {
            int numVPEarned = (int) ((rc.getTeamBullets() - BULLET_THRESHOLD) / rc.getVictoryPointCost());
            rc.donate(numVPEarned * rc.getVictoryPointCost());
        }
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
        for (int i = 0; i < 24; i++) {
            Direction dir = new Direction((float) (i * Math.PI / 12));
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
        boolean moved = false;
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
        boolean moved = false;
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
