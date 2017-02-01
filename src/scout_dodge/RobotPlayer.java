package scout_dodge;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    static final int MAX_GARDENERS = 4;

    static final int GARDENER_COUNT_IDX = GameConstants.BROADCAST_MAX_CHANNELS-1;

    // Variables that can be set at beginning of run() method
    static int NUM_ARCHONS;
    static MapLocation[] ENEMY_ARCHON_LOCS;
    static Team FRIENDLY;
    static Team ENEMY;

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
        NUM_ARCHONS = ENEMY_ARCHON_LOCS.length;

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case TANK:
                runTank();
                break;
            case SCOUT:
                runScout();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
        }
    }

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        int cnt = 0;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

                Direction dir = randomDirection();
                if (rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
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

    static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        boolean builtScout = false;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

                Direction dir = randomDirection();
                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < 0.5) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                }
                else if (rc.canBuildRobot(RobotType.SCOUT, dir) && Math.random() < 0.05) {
                    rc.buildRobot(RobotType.SCOUT, dir);
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

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

                RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                boolean fired = false;
                if (nearbyRobots.length > 0 && rc.canFireTriadShot()) {
                    for (RobotInfo robot : nearbyRobots) {
                        if (robot.type == RobotType.SCOUT) {
                            rc.fireTriadShot(rc.getLocation().directionTo(robot.location));
                            fired = true;
                        }
                    }
                }

                if (!fired) {
                    tryMove(randomDirection());
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

    static void runTank() throws GameActionException {
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

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

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

                MapLocation myLoc = rc.getLocation();

                BulletInfo[] nearbyBullets = rc.senseNearbyBullets();
                if (nearbyBullets.length > 0) {
                    MapLocation intendedLoc = rc.getLocation();
                    for (BulletInfo bullet : nearbyBullets) {
                        Direction bulletDir = bullet.dir;
                        MapLocation p1 = bullet.location;
                        // If the bullet is moving away and is farther than stride radius, ignore
                        if (p1.distanceTo(myLoc) > RobotType.SCOUT.strideRadius &&
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
                        System.out.println(distance + " " + (20.0f - distance) + " ||| ");
//                        rc.setIndicatorLine(myLoc, myLoc.add(dir, 10 - distance), 0, 0, 0);
                        intendedLoc = intendedLoc.add(dir, 20.0f - distance);
                    }
                    rc.setIndicatorLine(myLoc, intendedLoc, 0, 0, 0);
                    rc.setIndicatorDot(myLoc, 255, 0, 0);
                    tryMove(intendedLoc, 10, 17);
                }
                else {
//                    tryMove(randomDirection());
                    tryMove(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);
                }

                RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (nearbyRobots.length > 0 && rc.canFireSingleShot()) {
                    rc.fireSingleShot(rc.getLocation().directionTo(nearbyRobots[0].location));
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

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

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

    static boolean tryShake() throws GameActionException {
        TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        for (TreeInfo tree : nearbyNeutralTrees) {
            if (rc.canShake(tree.getID()) && tree.containedBullets > 0) {
                rc.shake(tree.getID());
                return true;
            }
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
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}
