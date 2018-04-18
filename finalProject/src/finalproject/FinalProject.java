package finalproject;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.jogamp.opengl.*;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.nativewindow.ScalableSurface;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Font;

import javax.swing.JFrame;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import java.util.ArrayList;

class FinalProject extends JFrame implements GLEventListener, KeyListener, MouseListener, MouseMotionListener, ActionListener {
    TextRenderer renderer;
    int fps = 30;
    int frameCount = 0;
    int score = 0;
    int level = 1;
    int lives = 3;
    
    int game_mode = 2; //0 play, 1 damaged, 2 over, 3 reset

    /* This defines the objModel class, which takes care
	 * of loading a triangular mesh from an obj file,
	 * estimating per vertex average normal,
	 * and displaying the mesh.
     */
    class objModel {

        public FloatBuffer vertexBuffer;
        public IntBuffer faceBuffer;
        public FloatBuffer normalBuffer;
        public Point3f center;
        public int num_verts;		// number of vertices
        public int num_faces;		// number of triangle faces

        public void Draw() {
            vertexBuffer.rewind();
            normalBuffer.rewind();
            faceBuffer.rewind();
            gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);

            gl.glVertexPointer(3, GL2.GL_FLOAT, 0, vertexBuffer);
            gl.glNormalPointer(GL2.GL_FLOAT, 0, normalBuffer);

            gl.glDrawElements(GL2.GL_TRIANGLES, num_faces * 3, GL2.GL_UNSIGNED_INT, faceBuffer);

            gl.glDisableClientState(GL2.GL_VERTEX_ARRAY);
            gl.glDisableClientState(GL2.GL_NORMAL_ARRAY);
        }

        public objModel(String filename) {
            /* load a triangular mesh model from a .obj file */
            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(filename));
            } catch (IOException e) {
                System.out.println("Error reading from file " + filename);
                System.exit(0);
            }

            center = new Point3f();
            float x, y, z;
            int v1, v2, v3;
            float minx, miny, minz;
            float maxx, maxy, maxz;
            float bbx, bby, bbz;
            minx = miny = minz = 10000.f;
            maxx = maxy = maxz = -10000.f;

            String line;
            String[] tokens;
            ArrayList<Point3f> input_verts = new ArrayList<Point3f>();
            ArrayList<Integer> input_faces = new ArrayList<Integer>();
            ArrayList<Vector3f> input_norms = new ArrayList<Vector3f>();
            try {
                while ((line = in.readLine()) != null) {
                    if (line.length() == 0) {
                        continue;
                    }
                    switch (line.charAt(0)) {
                        case 'v':
                            if(line.charAt(1) == 't') {
                                break;
                            }
                            tokens = line.split("[ ]+");
                            x = Float.valueOf(tokens[1]);
                            y = Float.valueOf(tokens[2]);
                            z = Float.valueOf(tokens[3]);
                            minx = Math.min(minx, x);
                            miny = Math.min(miny, y);
                            minz = Math.min(minz, z);
                            maxx = Math.max(maxx, x);
                            maxy = Math.max(maxy, y);
                            maxz = Math.max(maxz, z);
                            input_verts.add(new Point3f(x, y, z));
                            center.add(new Point3f(x, y, z));
                            break;
                        case 'f':
                            tokens = line.split("[ ]+");
                            tokens[1] = tokens[1].split("/")[0];
                            tokens[2] = tokens[2].split("/")[0];
                            tokens[3] = tokens[3].split("/")[0];
                            v1 = Integer.valueOf(tokens[1]) - 1;
                            v2 = Integer.valueOf(tokens[2]) - 1;
                            v3 = Integer.valueOf(tokens[3]) - 1;
                            input_faces.add(v1);
                            input_faces.add(v2);
                            input_faces.add(v3);
                            break;
                        default:
                            continue;
                    }
                }
                in.close();
            } catch (IOException e) {
                System.out.println("Unhandled error while reading input file.");
            }

            System.out.println("Read " + input_verts.size()
                    + " vertices and " + input_faces.size() + " faces.");

            center.scale(1.f / (float) input_verts.size());

            bbx = maxx - minx;
            bby = maxy - miny;
            bbz = maxz - minz;
            float bbmax = Math.max(bbx, Math.max(bby, bbz));

            for (Point3f p : input_verts) {

                p.x = (p.x - center.x) / bbmax;
                p.y = (p.y - center.y) / bbmax;
                p.z = (p.z - center.z) / bbmax;
            }
            center.x = center.y = center.z = 0.f;

            /* estimate per vertex average normal */
            int i;
            for (i = 0; i < input_verts.size(); i++) {
                input_norms.add(new Vector3f());
            }

            Vector3f e1 = new Vector3f();
            Vector3f e2 = new Vector3f();
            Vector3f tn = new Vector3f();
            for (i = 0; i < input_faces.size(); i += 3) {
                v1 = input_faces.get(i + 0);
                v2 = input_faces.get(i + 1);
                v3 = input_faces.get(i + 2);

                e1.sub(input_verts.get(v2), input_verts.get(v1));
                e2.sub(input_verts.get(v3), input_verts.get(v1));
                tn.cross(e1, e2);
                input_norms.get(v1).add(tn);

                e1.sub(input_verts.get(v3), input_verts.get(v2));
                e2.sub(input_verts.get(v1), input_verts.get(v2));
                tn.cross(e1, e2);
                input_norms.get(v2).add(tn);

                e1.sub(input_verts.get(v1), input_verts.get(v3));
                e2.sub(input_verts.get(v2), input_verts.get(v3));
                tn.cross(e1, e2);
                input_norms.get(v3).add(tn);
            }

            /* convert to buffers to improve display speed */
            for (i = 0; i < input_verts.size(); i++) {
                input_norms.get(i).normalize();
            }

            vertexBuffer = Buffers.newDirectFloatBuffer(input_verts.size() * 3);
            normalBuffer = Buffers.newDirectFloatBuffer(input_verts.size() * 3);
            faceBuffer = Buffers.newDirectIntBuffer(input_faces.size());

            for (i = 0; i < input_verts.size(); i++) {
                vertexBuffer.put(input_verts.get(i).x);
                vertexBuffer.put(input_verts.get(i).y);
                vertexBuffer.put(input_verts.get(i).z);
                normalBuffer.put(input_norms.get(i).x);
                normalBuffer.put(input_norms.get(i).y);
                normalBuffer.put(input_norms.get(i).z);
            }

            for (i = 0; i < input_faces.size(); i++) {
                faceBuffer.put(input_faces.get(i));
            }
            num_verts = input_verts.size();
            num_faces = input_faces.size() / 3;
        }
    }

    public void keyPressed(KeyEvent e) {        
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE:
            case KeyEvent.VK_Q:
                System.exit(0);
                break;
            case 'a':
            case 'A':
                //player_rotate = (player_rotate-2)%360;
                if(vel > 0){
                    vel = -1*acc;
                }
                else{
                    vel = vel - acc;
                    if(vel < -1*max_vel) vel = -1*max_vel;
                }
                
                break;
            case 'd':
            case 'D':
                //player_rotate = (player_rotate+2)%360;
                if(vel <0){
                    vel = acc;
                }
                else{
                    vel = vel + acc;
                    if(vel > max_vel) vel = max_vel;
                }
                break;
            case KeyEvent.VK_SPACE:
                if(game_mode == 0){
                    Projectile b = new Projectile(player_rotate, circle_rad-0.25f, -1*bulletSpeed);
                    bullets.add(b);
                }
                else if(game_mode == 2){
                    vel = 0;
                    game_mode = 3;
                    score = 0;
                    level = 0;
                    lives = 3;
                    player_rotate = 0f;
                    bullets.clear();
                    ufoBullets.clear();
                }
                break;      
            
        }

//        canvas.display();
    }

    /* GL, display, model transformation, and mouse control variables */
    private final GLCanvas canvas;
    private GL2 gl;
    private final GLU glu = new GLU();
    private FPSAnimator animator;

    private int winW = 800, winH = 800;
    private boolean wireframe = false;
    private boolean cullface = true;
    private boolean flatshade = false;

    private float xpos = 0, ypos = 0, zpos = 0;
    private float centerx, centery, centerz;
    private float roth = 0, rotv = 0;
    private float znear, zfar;
    private int mouseX, mouseY, mouseButton;
    private float motionSpeed, rotateSpeed;
    private float animation_speed = 1.0f;

    /* === YOUR WORK HERE === */
 /* Define more models you need for constructing your scene */
    private objModel rocket = new objModel("rocket.obj");
    private objModel ufo = new objModel("UFO.obj");
    private objModel beam = new objModel("beam.obj");
    private objModel ufoBullet = new objModel("ufoBullet.obj");
    
    private float circle_rad = 2.65f;
    private float enemy_rad = .1f;
    
    private float bulletSpeed = .1f;
    
    private float example_rotateT = 0.f;
    private float player_rotate = 0.f;
    
    private float def_fly_in = 0.5f;
    private float fly_in = def_fly_in;
    
    private float vel = 0f;
    private float max_vel = 2.5f;
    private float acc = 1.5f;
    private float stop_acc = .09f; 
    
    
    private ArrayList<Projectile> bullets = new ArrayList<Projectile>();
    private ArrayList<Projectile> ufoBullets = new ArrayList<Projectile>();
//    private float jump = 0.f;
//    private float jumpSmall = 0.f;
//    private float rot = 0.f;
//    private float birdFly = 0.f;
    
    
    /* Here you should give a conservative estimate of the scene's bounding box
	 * so that the initViewParameters function can calculate proper
	 * transformation parameters to display the initial scene.
	 * If these are not set correctly, the objects may disappear on start.
     */
    private float xmin = -2f, ymin = -2f, zmin = -2f;
    private float xmax = 2f, ymax = 2f, zmax = 2f;
    
    
    
    public void addScore(int num){
        score += num;
        level = score/20 + 1;
    }
    
    public void spawnUfoBullet(){
        float ang = (float)(int)(Math.random() * 361);
        float spd = ((float)((Math.random() * (level/2+1))+1))/100;
        Projectile bull = new Projectile(ang, 0, spd);
        ufoBullets.add(bull);
        //1-30 
        //.01 - .12
    }
    
    public double dist(double x1, double y1, double x2, double y2){
        return Math.sqrt(Math.pow((x2-x1), 2) + Math.pow((y2-y1), 2));
    }
    
    public boolean shipCollision(double ufoX, double ufoY){
        double ang = (double)player_rotate;
        if(ang < 0.0) ang = 360.0 + ang;
        else if(ang > 360.0) ang = ang - 360.0;
        
        double ang_rot = 360.0 - ang;
        
        double xPrime = ufoX * Math.cos(Math.toRadians(ang_rot)) - ufoY * Math.sin(Math.toRadians(ang_rot));
        double yPrime = ufoY * Math.cos(Math.toRadians(ang_rot)) + ufoX * Math.sin(Math.toRadians(ang_rot));
        
        if(yPrime > .1 || yPrime < -.1) return false;
        else if(xPrime > 2.85) return false;
        else return true;
    }
    
    public void checkCollisions(){
        double shipX = circle_rad * Math.cos(Math.toRadians(player_rotate));
        double shipY = circle_rad * Math.sin(Math.toRadians(player_rotate));
        int numUfoBullets = ufoBullets.size();
        for(int i = 0; i < numUfoBullets; i++){
            double ufoX = ufoBullets.get(i).distance* Math.cos(Math.toRadians(ufoBullets.get(i).angle));
            double ufoY = ufoBullets.get(i).distance* Math.sin(Math.toRadians(ufoBullets.get(i).angle));
            
            int numBullets = bullets.size();
            for(int j = 0; j < numBullets; j++){
                //Check collision with bullet vs UFO bullet
                
                double bulletX = bullets.get(j).distance* Math.cos(Math.toRadians(bullets.get(j).angle));
                double bulletY = bullets.get(j).distance* Math.sin(Math.toRadians(bullets.get(j).angle));
                double d = dist(ufoX, ufoY, bulletX, bulletY);
                if(d < .12){ //radius of bullet and ball
//                    System.out.println("Bop");
                    addScore(5);
                    if(i >= 0 && i < ufoBullets.size())
                    ufoBullets.remove(i);
                    bullets.remove(j);
                    i--;
                    j--;
                    numUfoBullets--;
                    numBullets--;
                }
                d = dist(bulletX, bulletY, shipX, shipY);
                if(d < .1 && bullets.get(j).speed > 0){
//                    System.out.println("Boom");
                    bullets.remove(j);
                    numBullets--;
                    j--;
                    lives--;
                    if(lives <= 0){
                        //reset game
                        game_mode = 2;
                    }
                }
            }
            
            //Check collision with ship
            
            double d = dist(ufoX, ufoY, shipX, shipY);
            
            if(d < .45 && shipCollision(ufoX, ufoY)){ //radius of ship and ball
//                System.out.println("Boom");
                ufoBullets.remove(i);
                numUfoBullets--;
                i--;
                lives--;
                if(lives <= 0){
                    //reset game
                    game_mode = 2;
                }
            }
        }
    }
    
    
    //Coloring Stuff
    float mat_ambient[] = {1f, .1f, .8f, 1f};
    float mat_ambient_silver[] = {.333f, .333f, .333f, 1f};//Ship
    float mat_ambient_red[] = {1f, 0f, 0f, 1f};//Bullets - Fix
    float mat_ambient_green[] = {.07f, .52f, 0f, 1f};//UFO - Maybe Blue
    float mat_ambient_brown[] = {.4f, .337f, 0f, 1f};
    
//    float mat_ambient_blue[] = {0f, .835f, 1f, 1f};
    float mat_ambient_orange[] = {0.74f, .39f, 0.1f, 1f};
//    float mat_ambient_brown[] = {.4f, .337f, 0f, 1f};

    float mat_diffuse[] = { .15f, .15f, .15f, 1f };
    float mat_diffuse_low[] = { .15f, .15f, .15f, 1f };
    float mat_diffuse_low2[] = { .35f, .35f, .35f, 1f };
    float mat_diffuse_high2[] = { .55f, .55f, .55f, 1f };
    float mat_diffuse_high[] = { .75f, .75f, .75f, 1f };

    float mat_specular[] = { .2f, .2f, .2f, 1f };
    float mat_specular_low[] = { .2f, .2f, .2f, 1f };
    float mat_specular_low2[] = { .4f, .4f, .4f, 1f };
    float mat_specular_high2[] = { .6f, .6f, .6f, 1f };
    float mat_specular_high[] = { .8f, .8f, .8f, 1f };

    float mat_shininess[] = { 0 }; //range[0,128]
    float mat_shininess1[] = { 32 }; //range[0,128]
    float mat_shininess2[] = { 64 }; //range[0,128]
    float mat_shininess3[] = { 96 }; //range[0,128]
    float mat_shininess4[] = { 128 }; //range[0,128]

    float mat_emission[] = { 0.0f, 0.0f, 0.0f, 1.0f };
    float mat_emission_red[] = { 1.0f, 0.0f, 0.0f, 0.5f };
    //End of Coloring stuff
    
    

    public void display(GLAutoDrawable drawable) {
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, wireframe ? GL2.GL_LINE : GL2.GL_FILL);
        gl.glShadeModel(flatshade ? GL2.GL_FLAT : GL2.GL_SMOOTH);
        if (cullface) {
            gl.glEnable(GL2.GL_CULL_FACE);
        } else {
            gl.glDisable(GL2.GL_CULL_FACE);
        }

        gl.glLoadIdentity();

        /* this is the transformation of the entire scene */
        gl.glTranslatef(-xpos, -ypos, -zpos);
        gl.glTranslatef(centerx, centery, centerz);
        gl.glRotatef(360.f - roth, 0, 1.0f, 0);
        gl.glRotatef(rotv, 1.0f, 0, 0);
        gl.glTranslatef(-centerx, -centery, -centerz);

        /* === YOUR WORK HERE === */ 
        
        if(game_mode == 0 || game_mode == 1){
            
            renderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
            renderer.setColor(1.0f, 0.2f, 0.2f, 0.8f);
            renderer.draw("Level: " + level, 0, drawable.getSurfaceHeight()-40);
            renderer.draw("Score: " + score, 0, drawable.getSurfaceHeight()-75);
            renderer.draw("Lives: " + lives, 0, drawable.getSurfaceHeight()-110);
            renderer.endRendering();
            
            frameCount++;
            if(frameCount >= fps){
                addScore(1);
                frameCount = 0;
            }
            
            //Color UFO
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_green,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_low,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission,0);

             gl.glRotatef(10*example_rotateT, 3f, 15f, 7f);
             gl.glScalef(.25f,.25f,.25f);
             ufo.Draw();
             gl.glScalef(4f,4f,4f);
             gl.glRotatef(-10*example_rotateT, 3f, 15f, 7f);
             
             //Color Ship
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_silver,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_high2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission,0);
            
             gl.glRotatef(player_rotate, 0, 0, 1f);
             gl.glTranslatef(circle_rad,0,0);
             //Draw rocket
                gl.glRotatef(-115f, 0, 1, 0);
                    gl.glRotatef(6*example_rotateT, 0, 0, 1);
                    gl.glScalef(.5f,.5f,.5f);
                    rocket.Draw();
                    gl.glScalef(2f,2f,2f);
                    gl.glRotatef(-6*example_rotateT, 0, 0, 1);
                gl.glRotatef(115f, 0, 1, 0);
             //End Draw Rocket
             gl.glTranslatef(-1*circle_rad,0,0);
             gl.glRotatef(-1*player_rotate, 0, 0, 1f);


             //Color Bullets
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_red,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_low,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission_red,0);
             
             int numBullets = bullets.size();
             for(int i = 0; i < numBullets; i++){
                gl.glRotatef(bullets.get(i).angle, 0, 0, 1f);
    //            gl.glTranslatef(circle_rad,0,0);

                gl.glTranslatef(bullets.get(i).distance,0,0);
                gl.glScalef(.1f,.1f,.1f);
                beam.Draw();
                gl.glScalef(10,10,10);
                gl.glTranslatef(-1*bullets.get(i).distance,0,0);

    //            gl.glTranslatef(-1*circle_rad,0,0);
                gl.glRotatef(-1*bullets.get(i).angle, 0, 0, 1f);
                bullets.get(i).distance = bullets.get(i).distance + bullets.get(i).speed;
                
                if(bullets.get(i).distance < enemy_rad && bullets.get(i).speed < 0){
                    bullets.get(i).speed = -1*bullets.get(i).speed;
//                    System.out.println("here");
                }
                if(bullets.get(i).distance > 3*circle_rad){
                    bullets.remove(i);
                    i--;
                    numBullets--;
                }
             }

             int randomNum = (int)(Math.random() * 31);
             if(level > randomNum){
                 spawnUfoBullet();
             }
             
             //Color UFO Bullets
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_orange,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_high2,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission,0);

             numBullets = ufoBullets.size();
             for(int i = 0; i < numBullets; i++){
                gl.glRotatef(ufoBullets.get(i).angle, 0, 0, 1f);

                gl.glTranslatef(ufoBullets.get(i).distance,0,0);
                gl.glScalef(.2f,.2f,.2f);
                ufoBullet.Draw();
                gl.glScalef(5,5,5);
                gl.glTranslatef(-1*ufoBullets.get(i).distance,0,0);

                gl.glRotatef(-1*ufoBullets.get(i).angle, 0, 0, 1f);
                ufoBullets.get(i).distance = ufoBullets.get(i).distance + ufoBullets.get(i).speed;

                if(ufoBullets.get(i).distance > 2*circle_rad){
                    ufoBullets.remove(i);
                    i--;
                    numBullets--;
                }
             }

             checkCollisions();
             player_rotate += vel;
             player_rotate = player_rotate%360;
             
            if(vel > 0) vel -= stop_acc;
            else vel += stop_acc;
            float tmp = vel;
            if(tmp < 0) tmp = tmp * -1.0f;
            if(tmp <= stop_acc) vel = 0;
        }
        //-----------------------------------------------------------------------------------------------------------------------------------------------
        else if(game_mode == 2){
            
            renderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
            renderer.setColor(1.0f, 0.2f, 0.2f, 0.8f);
            renderer.draw("Space Ship Defense", drawable.getSurfaceWidth()/2 - 160, drawable.getSurfaceHeight()/2 + 32);
            renderer.draw("Press Space To Begin", drawable.getSurfaceWidth()/2 - 170, drawable.getSurfaceHeight()/2-50);
            
            if(score > 0 ){
                renderer.draw("Level: " + level, 0, drawable.getSurfaceHeight()-40);
                renderer.draw("Score: " + score, 0, drawable.getSurfaceHeight()-75);
                renderer.draw("Lives: " + lives, 0, drawable.getSurfaceHeight()-110);
            }
            
            renderer.endRendering();
            
            //Color UFO
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_green,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_low,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission,0);
            
            gl.glRotatef(example_rotateT, 0, 0, 1f);
             gl.glScalef(.25f,.25f,.25f);
             ufo.Draw();
             gl.glScalef(4f,4f,4f);
             gl.glRotatef(-1*example_rotateT, 0, 0, 1f);
             
             //Color Bullets
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_red,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_low,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission_red,0);
             
             int numBullets = bullets.size();
             for(int i = 0; i < numBullets; i++){
                gl.glRotatef(bullets.get(i).angle, 0, 0, 1f);

                gl.glTranslatef(bullets.get(i).distance,0,0);
                gl.glScalef(.1f,.1f,.1f);
                beam.Draw();
                gl.glScalef(10,10,10);
                gl.glTranslatef(-1*bullets.get(i).distance,0,0);

    //            gl.glTranslatef(-1*circle_rad,0,0);
                gl.glRotatef(-1*bullets.get(i).angle, 0, 0, 1f);
                bullets.get(i).distance = bullets.get(i).distance + bullets.get(i).speed;

                if(bullets.get(i).distance > 3*circle_rad){
                    bullets.remove(i);
                    i--;
                    numBullets--;
                }
             }
             
             //Color UFO Bullets
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_orange,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_high2,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
             gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission,0);

             numBullets = ufoBullets.size();
             for(int i = 0; i < numBullets; i++){
                gl.glRotatef(ufoBullets.get(i).angle, 0, 0, 1f);

                gl.glTranslatef(ufoBullets.get(i).distance,0,0);
                gl.glScalef(.2f,.2f,.2f);
                ufoBullet.Draw();
                gl.glScalef(5,5,5);
                gl.glTranslatef(-1*ufoBullets.get(i).distance,0,0);

                gl.glRotatef(-1*ufoBullets.get(i).angle, 0, 0, 1f);
                ufoBullets.get(i).distance = ufoBullets.get(i).distance + ufoBullets.get(i).speed;

                if(ufoBullets.get(i).distance > 2*circle_rad){
                    ufoBullets.remove(i);
                    i--;
                    numBullets--;
                }
             }
             //Press Space to Begin
        }
        //--------------------------------------------------------------------------------------------------------------------------------------
        else if(game_mode == 3){
            
            //Color UFO
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_green,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_low,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission,0);
            
             gl.glRotatef(example_rotateT, 0, 0, 1f);
             gl.glScalef(.25f,.25f,.25f);
             ufo.Draw();
             gl.glScalef(4f,4f,4f);
             gl.glRotatef(-1*example_rotateT, 0, 0, 1f);
             
             //Color Ship
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, mat_ambient_silver,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_DIFFUSE, mat_diffuse_low2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, mat_specular_high2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_SHININESS, mat_shininess2,0);
            gl.glMaterialfv(gl.GL_FRONT, gl.GL_EMISSION, mat_emission,0);
             
             gl.glRotatef(player_rotate, 0, 0, 1f);
             gl.glTranslatef(circle_rad + fly_in,0,0);
             //Draw rocket
                gl.glRotatef(-115f, 0, 1, 0);
                    gl.glRotatef(6*example_rotateT, 0, 0, 1);
                    gl.glScalef(.5f,.5f,.5f);
                    rocket.Draw();
                    gl.glScalef(2f,2f,2f);
                    gl.glRotatef(-6*example_rotateT, 0, 0, 1);
                gl.glRotatef(115f, 0, 1, 0);
             //End Draw Rocket
             gl.glTranslatef(-1*(circle_rad + fly_in),0,0);
             gl.glRotatef(-1*player_rotate, 0, 0, 1f);
             
             fly_in -= .01;
             if(fly_in <= 0){
                 fly_in = def_fly_in;
                 game_mode = 0;
                 example_rotateT = 0;
             }
        }
        else{
            game_mode = 0;
            example_rotateT = 0;
        }
         
        
         
         
         
         
        gl.glPushMatrix();	// push the current matrix to stack

        gl.glPopMatrix();

        /* increment example_rotateT */
        if (animator.isAnimating()) {
            example_rotateT += 1.0f * animation_speed;     
//            jump = ((float)Math.sin(example_rotateT/10)+1)/2;
        }
    }

    public FinalProject() {
        super("Assignment 2 -- FinalProject Modeling");
        final GLProfile glprofile = GLProfile.getMaxFixedFunc(true);
        GLCapabilities glcapabilities = new GLCapabilities(glprofile);
        canvas = new GLCanvas(glcapabilities);        
        canvas.setSurfaceScale(new float[] { ScalableSurface.IDENTITY_PIXELSCALE, ScalableSurface.IDENTITY_PIXELSCALE }); // potential fix for Retina Displays
        canvas.addGLEventListener(this);
        canvas.addKeyListener(this);
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        animator = new FPSAnimator(canvas, fps);	// create a 30 fps animator
        getContentPane().add(canvas);
        setSize(winW, winH);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
        animator.start();
        canvas.requestFocus();
    }

    public static void main(String[] args) {

        new FinalProject();
    }

    public void init(GLAutoDrawable drawable) {
        renderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 36));
        gl = drawable.getGL().getGL2();

        initViewParameters();
        gl.glClearColor(.1f, .1f, .1f, 1f);
        gl.glClearDepth(1.0f);

        // white light at the eye
        float light0_position[] = {0, 0, 1, 0};
        float light0_diffuse[] = {1, 1, 1, 1};
        float light0_specular[] = {1, 1, 1, 1};
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, light0_position, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, light0_diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, light0_specular, 0);

        //red light
        float light1_position[] = {-.1f, .1f, 0, 0};
        float light1_diffuse[] = {.6f, .05f, .05f, 1};
        float light1_specular[] = {.6f, .05f, .05f, 1};
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, light1_position, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, light1_diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPECULAR, light1_specular, 0);

        //blue light
        float light2_position[] = {.1f, .1f, 0, 0};
        float light2_diffuse[] = {.05f, .05f, .6f, 1};
        float light2_specular[] = {.05f, .05f, .6f, 1};
        gl.glLightfv(GL2.GL_LIGHT2, GL2.GL_POSITION, light2_position, 0);
        gl.glLightfv(GL2.GL_LIGHT2, GL2.GL_DIFFUSE, light2_diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT2, GL2.GL_SPECULAR, light2_specular, 0);

        float lmodel_ambient[] = {1.0f, 1.0f, 1.0f, 1.0f};
        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, lmodel_ambient, 0);
        gl.glLightModeli(GL2.GL_LIGHT_MODEL_TWO_SIDE, 1);

        gl.glEnable(GL2.GL_NORMALIZE);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_LIGHT1);
        gl.glEnable(GL2.GL_LIGHT2);

        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LESS);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST);
        gl.glCullFace(GL2.GL_BACK);
        gl.glEnable(GL2.GL_CULL_FACE);
        gl.glShadeModel(GL2.GL_SMOOTH);
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        winW = width;
        winH = height;

        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(45.f, (float) width / (float) height, znear, zfar);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    public void mousePressed(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        mouseButton = e.getButton();
        canvas.display();
    }

    public void mouseReleased(MouseEvent e) {
        mouseButton = MouseEvent.NOBUTTON;
        canvas.display();
    }

    public void mouseDragged(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        if (mouseButton == MouseEvent.BUTTON3) {
            zpos -= (y - mouseY) * motionSpeed;
            mouseX = x;
            mouseY = y;
            canvas.display();
        } else if (mouseButton == MouseEvent.BUTTON2) {
            xpos -= (x - mouseX) * motionSpeed;
            ypos += (y - mouseY) * motionSpeed;
            mouseX = x;
            mouseY = y;
            canvas.display();
        } else if (mouseButton == MouseEvent.BUTTON1) {
            roth -= (x - mouseX) * rotateSpeed;
            rotv += (y - mouseY) * rotateSpeed;
            mouseX = x;
            mouseY = y;
            canvas.display();
        }
    }

    /* computes optimal transformation parameters for OpenGL rendering.
	 * this is based on an estimate of the scene's bounding box
     */
    void initViewParameters() {
        roth = rotv = 0;

        float ball_r = (float) Math.sqrt((xmax - xmin) * (xmax - xmin)
                + (ymax - ymin) * (ymax - ymin)
                + (zmax - zmin) * (zmax - zmin)) * 0.707f;

        centerx = (xmax + xmin) / 2.f;
        centery = (ymax + ymin) / 2.f;
        centerz = (zmax + zmin) / 2.f;
        xpos = centerx;
        ypos = centery;
        zpos = ball_r / (float) Math.sin(45.f * Math.PI / 180.f) + centerz;

        znear = 0.01f;
        zfar = 1000.f;

        motionSpeed = 0.002f * ball_r;
        rotateSpeed = 0.1f;

    }

    // these event functions are not used for this assignment
    public void dispose(GLAutoDrawable glautodrawable) {
    }

    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void actionPerformed(ActionEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }
}
