package man;

import java.awt.geom.Point2D;
import java.awt.Color;
import java.awt.geom.Rectangle2D;

import java.util.ArrayList;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitByBulletEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class CoronaVirus extends AdvancedRobot { 
    int direction = 1;                                      

    public Point2D.Double location;                          
    public Point2D.Double enemy_location; 
    
    public static Rectangle2D.Double field = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);

    public static double enemy_energy = 100.0;                    
    public static double WALL = 160;                      

    public static int BINS = 47;                               
    public static double surf_stats[] = new double[BINS];  

    public ArrayList<EnemyWave> enemy_waves;                    
    public ArrayList<Integer> surf_directions;                 
    public ArrayList<Double> surf_abs_bearings;                 
    
    ArrayList<WaveBullet> waves = new ArrayList<WaveBullet>(); 
    static int[] stats = new int[31];                          
    
    /**
	* run: Comportamento padrão ao longo das rodadas
	* 
	* Define as cores do bot e o radar estará em constante movimento
	*/
    public void run() {
    	setColors(Color.BLACK, Color.BLACK, Color.ORANGE, Color.WHITE, Color.PINK);   

    	setAdjustGunForRobotTurn(true);   
    	setAdjustRadarForGunTurn(true); 

    	enemy_waves = new ArrayList<EnemyWave>();
        surf_directions = new ArrayList<Integer>();
        surf_abs_bearings = new ArrayList<Double>();

    	do {
   	        if(getRadarTurnRemaining() == 0.0) {
   	            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);   	
            }

   	        execute();
   	    } while(true);    	  
    }

    /**
    * onScannedRobot: Adiciona às estruturas de dados os últimos dados recolhidos pelo scan
    *
    * Cria a wave se detetar uma descida de energia do inimigo entre 0.09 e 3.01
    */
    public void onScannedRobot(ScannedRobotEvent event) {
    	location = new Point2D.Double(getX(), getY());

        double lateral_velocity = getVelocity()*Math.sin(event.getBearingRadians());         
        double abs_bearing = event.getBearingRadians() + getHeadingRadians();                
        double bullet_power = enemy_energy - event.getEnergy();            

        surf_directions.add(0, (lateral_velocity >= 0) ? 1 : -1);                       
        surf_abs_bearings.add(0, abs_bearing + Math.PI);                                 
        
        if(bullet_power<3.01 && bullet_power>0.09 && surf_directions.size()>2) {
            EnemyWave wave = new EnemyWave();
            
            wave.fire_time = getTime() - 1;
            wave.bullet_velocity = EnemyWave.bulletVelocity(bullet_power);
            wave.distance_traveled = EnemyWave.bulletVelocity(bullet_power);
            wave.direction = surf_directions.get(2).intValue();
            wave.direct_angle = surf_abs_bearings.get(2).doubleValue(); 
            wave.fire_location = (Point2D.Double)enemy_location.clone();

            enemy_waves.add(ew);
        }

        enemy_energy = event.getEnergy();
        enemy_location = EnemyWave.project(location, abs_bearing, event.getDistance());

        updateWaves();
        doSurfing();
        widthLock(event);
        guessFactoryTarget(event);
	}

    /**
    * guessFactoryTarget: Verifica se a wave alcançou o inimigo
    *
    * Se o inimigo estiver a mexer-se dispara para a última localização conhecida
    */
    private void guessFactoryTarget(ScannedRobotEvent event) {
        double abs_bearing = event.getBearingRadians() + getHeadingRadians();
        double enemy_x = getX() + Math.sin(abs_bearing) * event.getDistance();
        double enemy_y = getY() + Math.cos(abs_bearing) * event.getDistance();
        
        for(int i=0; i<waves.size(); i++) {
            WaveBullet current_wave = (WaveBullet)waves.get(i);

            if(current_wave.checkHit(enemy_x, enemy_y, getTime())) {
                waves.remove(current_wave);
                i--;
            }
        }
        
        double power = Math.min(3, Math.max(0,bullet_power(event)));
        
        if(event.getVelocity() != 0) {
            if(Math.sin(event.getHeadingRadians()-abs_bearing)*event.getVelocity() < 0) {
                direction = -1;
            }
            else {
                direction = 1;
            }
        }
        else {
            direction = 1;
        }

        WaveBullet new_wave = new WaveBullet(getX(), getY(), abs_bearing, power, direction, getTime(), stats);
            
        int best_index = 15;
        
        for(int i=0; i<31; i++) {
            if(stats[best_index] < stats[i]) {
                best_index = i;
            }
        }
            
        double guess_factor = (double)(best_index - (stats.length - 1) / 2)/ ((stats.length - 1) / 2);
        double angle_offset = direction * guess_factor * new_wave.maxEscapeAngle();
        double gun_adjust = Utils.normalRelativeAngle(abs_bearing - getGunHeadingRadians() + angle_offset);
        setTurnGunRightRadians(gun_adjust);
        
        if(getGunHeat() == 0 && gun_adjust < Math.atan2(9, event.getDistance()) && setFireBullet(power) != null) {
            waves.add(new_wave);
        } 
    }

    /**
    * widthLock: Mantem um arco de 72º em que o inimigo está no centro
    *
    * Sempre que o inimigo sai do arco o bot ajusta para compensar pelo lado que perdeu o bot de vista
    */
	private void widthLock(ScannedRobotEvent event) {
        double angle_to_Enemy = getHeadingRadians() + event.getBearingRadians();                      
        double radar_turn = Utils.normalRelativeAngle(angle_to_Enemy - getRadarHeadingRadians());
        double extra_turn = Math.min(Math.atan(36.0/event.getDistance()), Rules.RADAR_TURN_RATE_RADIANS);

        if(radar_turn < 0) {
            radar_turn -= extra_turn;
        }
        else {
            radar_turn += extra_turn;
        }
        
        setTurnRadarRightRadians(radar_turn);
    }

	/**
    * bullet_power: Retorna tamanho da bala consoante a energia e distância ao inimigo
    */
    private double bulletPower(ScannedRobotEvent event) {    	
    	if(getEnergy() > 90) {
    		if(event.getDistance() < 200) {
    			return Rules.MAX_BULLET_POWER;
            }
        }
    	else if(getEnergy() < 20) {
    		return Rules.MIN_BULLET_POWER;
        }
    	else {
    		if(event.getDistance() > 400) {
    			return Rules.MIN_BULLET_POWER;				
            }
        }

    	return (Rules.MAX_BULLET_POWER+Rules.MIN_BULLET_POWER)/2;
    }

	/**
    * updateWaves: Para cada wave em enemy_waves fazer: se a wave já tiver passado pelo bot -> eliminá-la
    *
    * A wave já passou pelo bot se a distância entre location e fire_location for menor que a distância percorrida pela wave
    * A razao de adicionar um 50 extra é apenas para dar algum espaço extra para rastrear o evento onHitByBullet 
    */
    public void updateWaves() {
        for(int x=0; x<enemy_waves.size(); x++) {
            EnemyWave wave = enemy_waves.get(x);
            wave.distance_traveled = (getTime() - wave.fire_time) * wave.bullet_velocity;
            
            if(wave.distance_traveled > location.distance(wave.fire_location)+50) {
                enemy_waves.remove(x);
                x--;
            }
        }
    }

    /**
    * getClosestSurfableWave: Para cada wave calcular a que distância está do bot e retorna a mais próxima
    */
    public EnemyWave getClosestSurfableWave() {
        double closest_distance = 50000;
        EnemyWave surf_wave = null;

        for(int x=0; x<enemy_waves.size(); x++) {
            EnemyWave wave = enemy_waves.get(x);
            double distance = location.distance(wave.fire_location) - wave.distance_traveled;

            if(distance>wave.bullet_velocity && distance<closest_distance) {
                surf_wave = wave;
                closest_distance = distance;
            }
        }

        return surf_wave;
    }

    /**
    * getFactorIndex : O "offsetAngle" é o ângulo para que o inimigo apontou para acertar no bot
    */
    public static int getFactorIndex(EnemyWave wave, Point2D.Double target_location) {
        double angle_offset = (EnemyWave.absoluteBearing(wave.fire_location, target_location) - wave.direct_angle);
        double factor = Utils.normalRelativeAngle(angle_offset)/EnemyWave.maxEscapeAngle(wave.bullet_velocity) * wave.direction;

        return (int)EnemyWave.limit(0, (factor*((BINS-1)/2))+((BINS-1)/2), BINS-1);
    }

    /**
    * logHit: Em vez de so aumentar o "BIN" retornado pelo "guessFactor" uniformiza com os "BINS" ao seu redor cada vez com um valor menor
    *
    * Dada a EnemyWave em que a bala estava e o ponto em que fomos atingidos, atualize nossa matriz de estatísticas para refletir o perigo nessa área
    */
    public void logHit(EnemyWave wave, Point2D.Double target_location) {
        int index = getFactorIndex(wave, target_location);
        
        for(int x=0; x<BINS; x++) {
            surf_stats[x] += 1.0/Math.pow(Math.abs(x-index)+1, 2);
        }
    }

    /**
    * onHitByBullet: Verifica qual a wave que atingiu o bot, e para cada uma verifica se está a menos de 50 unidades do bot e se a velocidade da bala que atingiu o bot é igual à bala que foi disparada
    *
    * Chama logHit para fazer update das estaticas    
    */
    public void onHitByBullet(HitByBulletEvent event) {
        if(!enemy_waves.isEmpty()) {
            Point2D.Double hit_bullet_location = new Point2D.Double(event.getBullet().getX(), event.getBullet().getY());
            EnemyWave hit_wave = null;

            for(int x=0; x<enemy_waves.size(); x++) {
                EnemyWave wave = enemy_waves.get(x);

                if(Math.abs(wave.distance_traveled-location.distance(wave.fire_location))<50 && Math.abs(EnemyWave.bullet_velocity(event.getBullet().getPower())-wave.bullet_velocity)<0.001) {
                    hit_wave = wave;
                    break;
                }
            }

            if(hit_wave != null) {
                logHit(hit_wave, hit_bullet_location);
                enemy_waves.remove(enemy_waves.lastIndexOf(hit_wave)); 
            }
        }
    }

    public void onBulletHitBullet(BulletHitEvent e) {
        if(!enemy_waves.isEmpty()) {
            Point2D.Double hit_bullet_location = new Point2D.Double(e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hit_wave = null;

            for(int x=0; x<enemy_waves.size(); x++) {
                EnemyWave wave = enemy_waves.get(x);

                if(Math.abs(wave.distance_traveled-location.distance(wave.fire_location))<50 && Math.abs(EnemyWave.bulletVelocity(e.getBullet().getPower())-wave.bullet_velocity)<0.001) {
                    hit_wave = wave;
                    break;
                }
            }

            if(hit_wave != null) {
                logHit(hit_wave, hit_bullet_location);
                enemy_waves.remove(enemy_waves.lastIndexOf(hit_wave));   
            }
        }
    }

    public Point2D.Double predictPosition(EnemyWave surf_wave, int direction) {
        Point2D.Double predicted_position = (Point2D.Double)location.clone();
        double predicted_velocity = getVelocity();
        double predicted_heading = getHeadingRadians();
        double max_turning;
        double move_angle;
        double move_dir;
        int counter = 0; 
        boolean intercepted = false;

        do { 
            move_angle = EnemyWave.wallSmoothing(predicted_position, EnemyWave.absoluteBearing(surf_wave.fire_location, predicted_position)+(direction*(Math.PI/2)), direction) - predicted_heading;
            move_dir = 1;

            if(Math.cos(move_angle) < 0) {
                move_angle += Math.PI;
                move_dir = -1;
            }

            move_angle = Utils.normalRelativeAngle(move_angle);

            max_turning = Math.PI/720d*(40d-3d*Math.abs(predicted_velocity));                                
            predicted_heading = Utils.normalRelativeAngle(predicted_heading + EnemyWave.limit(-max_turning, move_angle, max_turning));

            predicted_velocity += (predicted_velocity * move_dir < 0 ? 2*move_dir : move_dir);                  
            predicted_velocity = EnemyWave.limit(-8, predicted_velocity, 8);
            predicted_position = EnemyWave.project(predicted_position, predicted_heading, predicted_velocity); 

            counter++;

            if(predicted_position.distance(surf_wave.fire_location) < surf_wave.distance_traveled+(counter*surf_wave.bullet_velocity)+surf_wave.bullet_velocity) {
                intercepted = true;
            }
        } while(!intercepted && counter<500);

        return predicted_position;
    }

    /**
    * checkDanger: Retorna o valor de risco para a direção pedida
    */
    public double checkDanger(EnemyWave surf_wave, int direction) {
        int index = getFactorIndex(surf_wave, predictPosition(surf_wave, direction));

        return surf_stats[index];
    }

    /**
    * Prevê a posição do bot quando a wave o interceta
    *
    * Chama o "checkDanger" para perceber o risco da posição prevista para as duas direções e escolhe a direção mais segura
    */ 
    public void doSurfing() {
        EnemyWave surf_wave = getClosestSurfableWave();

        if(surf_wave == null) { 
            return;
        }

        double dangerLeft = checkDanger(surf_wave, -1);
        double dangerRight = checkDanger(surf_wave, 1);

        double go_angle = EnemyWave.absoluteBearing(surf_wave.fire_location, location);

        if(dangerLeft < dangerRight) {
            go_angle = EnemyWave.wallSmoothing(location, go_angle-(1.25), -1);
        } 
        else {
            go_angle = EnemyWave.wallSmoothing(location, go_angle+(1.25), 1);
        }

        EnemyWave.setBackAsFront(this, go_angle);
    }

}

/**
* Onda gerada sempre que o inimigo dispara uma bala
*/
class EnemyWave {
    Point2D.Double fire_location;    
    long fire_time;                  
    double bullet_velocity;          
    double distance_traveled;        
    double direct_angle;         
    int direction; 
    
    /**
	* wallSmoothing: Se o bot tem uma parede à frente ou atrás a uma distância inferior a 160 unidades altera o ângulo
    */
	public static double wallSmoothing(Point2D.Double location, double angle, int orientation) {
	    while(!(First.field.contains(project(location, angle, First.WALL)))) {
	        angle += orientation*0.05;
	    }
	
        return angle;
	}

	/**
	* project: Simula o posição de um robô consoante uma posição, o ângulo entre a posição e o robô e a distância a percorrer
	*/
	public static Point2D.Double project(Point2D.Double location, double angle, double length) {
	    return new Point2D.Double(location.x+Math.sin(angle)*length, location.y+Math.cos(angle)*length);
	}

	/**
	* absoluteBearing: Calcula o ângulo entre dois pontos
	*/
	public static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
	    return Math.atan2(target.x-source.x, target.y-source.y);
	}
	
	public static double limit(double min, double value, double max) {
	    return Math.max(min, Math.min(value, max));
	}
	
	public static double bulletVelocity(double power) {
	    return (20.0 - (3.0*power));
	}
	
    /**
    * maxEscapeAngle: Calcula o ângulo máximo que o inimigo pode ter percorrido consoante a velociade da bala e a velocidade do bot
    *
    * Assumiu-se que o bot anda sempre à velocidade máxima (8.0)
	*/
	public static double maxEscapeAngle(double velocity) {
	    return Math.asin(8.0/velocity);
	}
	
	public static void setBackAsFront(AdvancedRobot robot, double go_angle) {
	    double angle = Utils.normalRelativeAngle(go_angle - robot.getHeadingRadians());
	    
        if(Math.abs(angle) > (Math.PI/2)) {
	        if(angle < 0) {
	            robot.setTurnRightRadians(Math.PI + angle);
	        } 
            else {
	            robot.setTurnLeftRadians(Math.PI - angle);
	        }

	        robot.setBack(100);
	    }
	    else {
	        if(angle < 0) {
	            robot.setTurnLeftRadians(-1*angle);
	        }
	        else {
	            robot.setTurnRightRadians(angle);
	        }
	        
            robot.setAhead(100);
	    }
	}
}

/**
* Representa a wave dos tiros disparados pelo bot
*/
class WaveBullet {
	private double x;
    private double y;        
	private double start_bearing; 
    private double power;
	private long time;                
	private int direction;                 
	private int[] stats;
	
	public WaveBullet(double x, double y, double abs_bearing, double power, int direction, long time, int[] stats) {
		this.x = x;
		this.y = y;
		this.abs_bearing = abs_bearing;
		this.power = power;
		this.direction = direction;
		this.time = time;
		this.stats = stats;
	}

	public double getBulletSpeed() {
		return 20-(power*3);
	}
	
	public double maxEscapeAngle() {
		return Math.asin(8/getBulletSpeed());
	}

	/**
	* checkHit: retorna true se a wave atingiu o inmigo, ou false se ainda não
	*/
	public boolean checkHit(double enemy_x, double enemy_y, long current_time) {
		if(Point2D.distance(x, y, enemy_x, enemy_y) <= (current_time-time)*getBulletSpeed()) {
			double desired_direction = Math.atan2(enemy_x-x, enemy_y-y);
			double angle_offset = Utils.normalRelativeAngle(desired_direction-abs_bearing);
			double guess_factor = Math.max(-1, Math.min(1, angle_offset/maxEscapeAngle())) * direction;
			int index = (int)Math.round((stats.length-1)/2 * (guess_factor+1));
			stats[index]++;
			
            return true;
		}

		return false;
	}
}
