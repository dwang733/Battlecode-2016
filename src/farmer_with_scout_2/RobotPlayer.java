package farmer_with_scout_2;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

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

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

                // Try to implement so spawn gardeners evenly between archons
                for (int i=0;i<12;i++) {
                    Direction dir = new Direction((float) (i * Math.PI / 6));
                    if (rc.canHireGardener(dir) && Math.random() < 0.5) {
                        rc.hireGardener(dir);
                        break;
                    }
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

        // Tells if gardener built its first disruptive scout
        boolean builtScout = false;
        // Control output of scouts
        final int SCOUT_COOLDOWN = 20;
        final float SCOUT_THRESHOLD = RobotType.SCOUT.bulletCost * 2.0f;
        int turnsSinceScout = 0;
        // The gap that the gardener will keep open to build units
        Direction gap = null;
        float prevHealth = rc.getHealth();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Check if team can win via Victory Points
//                if (rc.getTeamBullets() > GameConstants.VICTORY_POINTS_TO_WIN * GameConstants.BULLET_EXCHANGE_RATE ||
//                        rc.getRoundNum() == rc.getRoundLimit() - 1) {
//                    rc.donate(rc.getTeamBullets());
//                }

                // Build a disruptive scout as soon as gardener is created
                while (!builtScout) {
                    for (int i=0;i<6;i++) {
                        Direction dir = new Direction((float) (i * Math.PI / 3));
                        if (rc.canBuildRobot(RobotType.SCOUT, dir)) {
                            rc.buildRobot(RobotType.SCOUT, dir);
                            builtScout = true;
                            System.out.println("Built disruptive scout");
                            break;
                        }
                    }
                    // If disruptive scout can't be built, wait (scout more important than trees)
                    if (!builtScout) {
                        Clock.yield();
                    }
                }

                // Gardener under attack! Move!
                if (rc.getHealth() < prevHealth) {
//                    for (int i=(int)RobotType.GARDENER.strideRadius*10;i>0;i-=2) {
//                        for (int j=0;j<12;j++) {
//                            Direction dir = new Direction((float) (j * Math.PI / 6));
//                            if (rc.canMove(dir, i/10f)) {
//                                rc.move(dir, i/10f);
//                                break;
//                            }
//                        }
//                    }
                    System.out.println("Dodging bullets.");
                    System.out.println(dodgeBullets());
                    prevHealth = rc.getHealth();
                }
                else {
                    // Plant a tree if possible (always leave a gap for building units)
                    for (int i=0;i<6;i++) {
                        Direction dir = new Direction((float) (i * Math.PI / 3));
                        if (gap == null) {
                            if (rc.canPlantTree(dir)) {
                                gap = dir;
                            }
                        }
                        // Check that direction is not the gap
                        // P.S. Direction.equals(dir, epsilon) doesn't work?
                        else if (!(Math.abs(gap.radians - dir.radians) < 1e-3) && rc.canPlantTree(dir)) {
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

                    // Build a disruptive scout occasionally too
                    if (turnsSinceScout < SCOUT_COOLDOWN && rc.getTeamBullets() > SCOUT_THRESHOLD) {
                        for (int i=0;i<6;i++) {
                            Direction dir = new Direction((float) (i * Math.PI / 3));
                            if (rc.canBuildRobot(RobotType.SCOUT, dir)) {
                                rc.buildRobot(RobotType.SCOUT, dir);
                                turnsSinceScout = 0;
                                break;
                            }
                        }
                    }
                    turnsSinceScout += 1;
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
                    int targetPriority = getPriority(targetType);
                    // Find target with most priority
                    for (RobotInfo rb : nearbyEnemyRobots) {
                        RobotType rbType = rb.type;
                        int rbPriority = getPriority(rbType);
                        if (rbPriority == -1) continue; // Ignore unit
                        if (rbPriority < targetPriority || (rbPriority == targetPriority &&
                                myLoc.distanceTo(rb.location) < targetDist)) {
                            target = rb;
                            targetLoc = target.location;
                            targetType = target.type;
                            targetDist = targetLoc.distanceTo(myLoc);
                            targetPriority = getPriority(targetType);
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
                    if (rc.canFireSingleShot()) {
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

    /**
     * Get the priority of robot types for the scout.
     * @param rt The type of robot in question
     * @return The priority of that robot. A lower number means higher priority. -1 means ignore.
     */
    static int getPriority(RobotType rt) {
        switch (rt) {
            case GARDENER: return 1;
            case ARCHON: return 2;
            case LUMBERJACK: case SOLDIER: return 3;
            case SCOUT: return 4;
            case TANK: return 5;
        }
        return 10;
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
        return tryMove(dir,10,17);
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
        return tryMove(loc,10,17);
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

    static boolean dodgeBullets() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        final float MAX_SENSE_RANGE = Math.max(rc.getType().bulletSightRadius, 10);
        BulletInfo[] nearbyBullets = rc.senseNearbyBullets(MAX_SENSE_RANGE); // Magic number (change later?)
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
                System.out.println(distance + " " + (20.0f - distance) + " ||| ");
//                        rc.setIndicatorLine(myLoc, myLoc.add(dir, 10 - distance), 0, 0, 0);
                intendedLoc = intendedLoc.add(dir, 20.0f - distance);
            }
            rc.setIndicatorLine(myLoc, intendedLoc, 0, 0, 0);
            rc.setIndicatorDot(myLoc, 255, 0, 0);
            tryMove(intendedLoc, 10, 17);
            return true;
        }
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
