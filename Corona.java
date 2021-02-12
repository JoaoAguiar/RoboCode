package robot;

import robocode.*;
import java.awt.*;

// Robô do tipo: AdvancedRobot
public class Corona extends AdvancedRobot {
	double x_arena = 0;                      // Guarda o comprimento do campo
	double y_arena = 0;                      // Guarda a largura do campo
	double margin = 200;                     //
	double last_time_direction_changed = 0;  // 
	int direction = 1;                       // Direção
		
	// O robotcode chama o metodo run() quando a "luta" começa
	public void run() {
		x_arena = getBattleFieldWidth(); 
		y_arena = getBattleFieldHeight();
		
		setColors(Color.cyan, Color.white, Color.black, Color.red, Color.green);  // Cores do robot (body, gun, radar, bullet, scan)
	    setAdjustGunForRobotTurn(true);                                           // Ajusta a arma independente do body // Keep the gun still when we turn
	    setAdjustRadarForGunTurn(true);                                           // Ajusta o radar independente da gun //keep the radar still while we turn 
		turnRadarRightRadians(Double.POSITIVE_INFINITY);                          // Immediately turns the robot's radar to the right by radians //keep turning radar right
		
		// Robô vai fazer este ciclo até morrer
        while (true) {
        	move();     // Movimento
        	fire(200);  // Dispara
        	execute();  // Executa todas as ações pendentes ou continua executando as ações que estão em andamento
        }
    }

	// Makes sure that the bot doesn't hit walls and characterizes the movement of the robot
	public void move() {
		double x_position = getX();  // Posição x do robô
		double y_position = getY();  // Posição y do robô
		double margin_wall_bottom = margin;
		double margin_wall_left = margin;
		double margin_wall_top = y_arena - margin;
		double margin_wall_right = x_arena - margin;
		
		// desviar paredes
		if(x_position < margin_wall_left || y_position < margin_wall_bottom || x_position <= margin_wall_right || y_position <= margin_wall_top) {
			setMaxVelocity(0);  // Sets the maximum velocity of the robot measured in pixels/turn if the robot should move slower than Rules  
			direction = direction * (-1);
			setAhead(250 * direction);	//
			setMaxVelocity(8);
		}
		else {		
			// muda de direção de tempo em tempo
			if(getTime()-last_time_direction_changed > 20) { 
				last_time_direction_changed = getTime();
				direction = direction * (-1);
			}
			
			setTurnRight(75);  //
			setAhead(250 * direction); 
		}
		
	}
	
	// Fire function with bullet prediction
	public void fire(double distance_fire) {
		double power = Math.min((400/distance_fire), 3);		
		setFire(power);
	}	
	
	// When an enemy is scanned the radar is locked into him
	public void onScannedRobot(ScannedRobotEvent event) {	  
		double body_angle = getHeading(); //
		double robot_angle = event.getBearing(); //
        double angle_enemy = body_angle + robot_angle;  // enemies absolute bearing
        double velocity = event.getVelocity() * Math.sin(event.getHeading() - angle_enemy); // enemies velocity
        double gun_turn = robocode.util.Utils.normalRelativeAngleDegrees(angle_enemy - getGunHeading() + (velocity/20)); // amount to turn our gun
        
        setTurnGunRight(gun_turn); //turn our gun
        setTurnRadarLeft(getRadarTurnRemaining());  //lock on the radar
        setTurnRight(robocode.util.Utils.normalRelativeAngle(angle_enemy - getHeading() + (velocity/getVelocity()))); //drive towards the enemies predicted future location
        setAhead((event.getDistance()-75) * direction);//move forward
        
    	fire(event.getDistance());     // Dispara
    	execute();  // Executa todas as ações pendentes ou continua executando as ações que estão em andamento
	}
	
	// Restart everything after the round ends
	public void onRoundEnded() { 	
		x_arena = 0;
		y_arena = 0;
		margin = 200;
		direction = 1;
	}
	
    // Do a victory dance
    public void onWin(WinEvent event) {
        for(int i=0; i<50; i++) {
            turnRight(30);
            turnLeft(30);
        }
    }
}
