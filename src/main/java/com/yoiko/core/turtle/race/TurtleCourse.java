package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.TurtleSurface;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;

public final class TurtleCourse {
    public record Sample(double distance, double x, double z, double tangentX, double tangentZ,
                         double normalX, double normalZ, double curvature, double turnSign,
                         TurtleSurface surface) { }
    public record TrackPoint(double x,double z,double tangentX,double tangentZ,TurtleSurface surface){ }
    private record LaneTrack(List<double[]> points,double[] centerDistances,double[] actualDistances,double length){ }

    private static final double SAMPLE_SPACING = 0.25;
    /**
     * The start and finish share one seam on the closed course. Keeping a full visible approach on
     * both sides of that seam dry prevents a water section from touching either side of the line.
     */
    public static final double START_FINISH_SAFE_DISTANCE = 12.0;
    public static final double START_WAIT_LINE_DISTANCE = 0.8;
    public static final double START_WAIT_DISTANCE = 1.15;
    public static final double WATER_RATIO = .44;
    private final List<Sample> samples;
    private final double length;
    private final boolean beachTheme;
    private final long seed;
    private final String themeId;
    private final boolean neutralPhysics;
    private final List<LaneTrack> laneTracks;

    private TurtleCourse(List<Sample> samples, boolean beachTheme, long seed, String themeId) {
        this(samples,beachTheme,seed,themeId,false);
    }

    private TurtleCourse(List<Sample> samples, boolean beachTheme, long seed, String themeId,boolean neutralPhysics) {
        this.samples = List.copyOf(samples);
        this.length = samples.get(samples.size() - 1).distance();
        this.beachTheme = beachTheme;
        this.seed = seed;
        this.themeId = normalizeTheme(themeId, beachTheme);
        this.neutralPhysics=neutralPhysics;
        this.laneTracks=buildLaneTracks(this.samples);
    }

    public static TurtleCourse generate(BlockPos center, long seed, boolean beachTheme) {
        return generate(center,seed,beachTheme,defaultTheme(seed,beachTheme));
    }

    public static TurtleCourse generate(BlockPos center,long seed,boolean beachTheme,String themeId) {
        SplittableRandom random = new SplittableRandom(seed);
        List<double[]> raw = new ArrayList<>();
        // Course coordinates are actual world coordinates at block centres. Keeping the .5 here is
        // important: using integer coordinates made every turtle run half a block off the road centre.
        double cx=center.getX()+.5,cz=center.getZ()+.5;
        int template=random.nextInt(5);
        List<double[]> vertices=switch(template){
            case 1->List.of(point(cx,-25,cz,-15),point(cx,8,cz,-15),point(cx,8,cz,-5),
                    point(cx,25,cz,-5),point(cx,25,cz,15),point(cx,-8,cz,15),
                    point(cx,-8,cz,5),point(cx,-25,cz,5));
            case 2->List.of(point(cx,-25,cz,-15),point(cx,-8,cz,-15),point(cx,-8,cz,-3),
                    point(cx,8,cz,-3),point(cx,8,cz,-15),point(cx,26,cz,-15),
                    point(cx,26,cz,15),point(cx,-25,cz,15));
            case 3->List.of(point(cx,-26,cz,-15),point(cx,5,cz,-15),point(cx,5,cz,-5),
                    point(cx,17,cz,-5),point(cx,17,cz,-15),point(cx,26,cz,-15),
                    point(cx,26,cz,15),point(cx,-7,cz,15),point(cx,-7,cz,5),point(cx,-26,cz,5));
            case 4->List.of(point(cx,-25,cz,-15),point(cx,-3,cz,-15),point(cx,-3,cz,-5),
                    point(cx,11,cz,-5),point(cx,11,cz,-15),point(cx,26,cz,-15),
                    point(cx,26,cz,15),point(cx,-3,cz,15),point(cx,-3,cz,5),point(cx,-25,cz,5));
            default->List.of(point(cx,-25,cz,-15),point(cx,25,cz,-15),
                    point(cx,25,cz,15),point(cx,-25,cz,15));
        };
        roundedOrthogonalLoop(raw,vertices,template==0?6.0:5.0);
        rotateStartToPodiumStraight(raw,cx);

        List<Sample> samples = new ArrayList<>();
        double totalLength=0;
        for(int i=1;i<raw.size();i++)totalLength+=Math.hypot(raw.get(i)[0]-raw.get(i-1)[0],raw.get(i)[1]-raw.get(i-1)[1]);
        double distance = 0;
        for (int i = 0; i < raw.size(); i++) {
            double[] p = raw.get(i);
            double[] before = raw.get(Math.floorMod(i - 1, raw.size()));
            double[] after = raw.get((i + 1) % raw.size());
            if (i > 0) distance += Math.hypot(p[0] - raw.get(i - 1)[0], p[1] - raw.get(i - 1)[1]);
            double tx = after[0] - before[0];
            double tz = after[1] - before[1];
            double magnitude = Math.max(1.0e-8, Math.hypot(tx, tz));
            tx /= magnitude; tz /= magnitude;
            double inX=p[0]-before[0],inZ=p[1]-before[1];
            double outX=after[0]-p[0],outZ=after[1]-p[1];
            double cross=inX*outZ-inZ*outX;
            double dot=inX*outX+inZ*outZ;
            // Normalize the local heading change. The old cross-product magnitude was about 0.003 on
            // these sampled arcs, so every 90-degree corner was incorrectly treated as a straight.
            double angle=Math.atan2(cross,dot);
            double curvature=Math.min(1.0,Math.abs(angle)/Math.toRadians(2.0));
            double turnSign=Math.signum(angle);
            TurtleSurface surface = surfaceAt(distance, totalLength, beachTheme);
            samples.add(new Sample(distance, p[0], p[1], tx, tz, -tz, tx, curvature, turnSign, surface));
        }
        return new TurtleCourse(samples, beachTheme, seed, themeId);
    }

    public static TurtleCourse preset(BlockPos center, int presetIndex) {
        int index=Math.floorMod(presetIndex,6);boolean beach=index<3;
        long presetSeed=0x5EED_1000L+index*0x101L;TurtleCourse base=generate(center,presetSeed,beach);double target=base.length;double scale=1.0;
        List<Sample> adjusted=new ArrayList<>(base.samples.size());
        double cx=center.getX()+.5,cz=center.getZ()+.5;
        for(Sample sample:base.samples){double distance=sample.distance*scale;TurtleSurface surface=presetSurface(index,distance,target);adjusted.add(new Sample(distance,cx+(sample.x-cx)*scale,cz+(sample.z-cz)*scale,sample.tangentX,sample.tangentZ,sample.normalX,sample.normalZ,sample.curvature,sample.turnSign,surface));}
        Sample last=adjusted.get(adjusted.size()-1);adjusted.set(adjusted.size()-1,new Sample(target,last.x,last.z,last.tangentX,last.tangentZ,last.normalX,last.normalZ,last.curvature,last.turnSign,last.surface));
        return new TurtleCourse(adjusted,beach,presetSeed,defaultTheme(presetSeed,beach));
    }

    /** Uniform sand course for deterministic stat/strategy balance tests. */
    public static TurtleCourse validationUniform(BlockPos center,long seed){
        TurtleCourse base=generate(center,seed,false);List<Sample> uniform=new ArrayList<>(base.samples.size());
        for(Sample sample:base.samples)uniform.add(new Sample(sample.distance,sample.x,sample.z,sample.tangentX,sample.tangentZ,
                sample.normalX,sample.normalZ,sample.curvature,sample.turnSign,TurtleSurface.SAND));
        return new TurtleCourse(uniform,false,seed,"forest_pond",true);
    }

    public static TurtleCourse validationUniformScaled(BlockPos center,long seed,double scale){
        TurtleCourse base=validationUniform(center,seed);double cx=center.getX()+.5,cz=center.getZ()+.5;List<Sample> scaled=new ArrayList<>(base.samples.size());
        for(Sample sample:base.samples)scaled.add(new Sample(sample.distance*scale,cx+(sample.x-cx)*scale,cz+(sample.z-cz)*scale,
                sample.tangentX,sample.tangentZ,sample.normalX,sample.normalZ,sample.curvature,sample.turnSign,TurtleSurface.SAND));
        return new TurtleCourse(scaled,false,seed,"forest_pond",true);
    }

    private static TurtleSurface presetSurface(int index,double distance,double length){
        if(isStartFinishZone(distance,length))return startFinishSurface(index);
        if(waterAt(distance,length))return TurtleSurface.PUDDLE;
        return index<3?TurtleSurface.SAND:TurtleSurface.MUD;}

    private static TurtleSurface startFinishSurface(int presetIndex){return presetIndex<3?TurtleSurface.SAND:TurtleSurface.MUD;}

    private static void line(List<double[]> points, double x1, double z1, double x2, double z2) {
        double length = Math.hypot(x2-x1, z2-z1);
        int count = Math.max(1, (int) Math.ceil(length / SAMPLE_SPACING));
        for (int i=points.isEmpty()?0:1; i<=count; i++) {
            double t=(double)i/count;
            points.add(new double[]{x1+(x2-x1)*t,z1+(z2-z1)*t});
        }
    }

    private static double[] point(double cx,double dx,double cz,double dz){return new double[]{cx+dx,cz+dz};}

    /** Builds a closed orthogonal course with true quarter-circle corner fillets. */
    private static void roundedOrthogonalLoop(List<double[]> points,List<double[]> vertices,double radius){
        int count=vertices.size();double[][] entry=new double[count][2],exit=new double[count][2];
        for(int i=0;i<count;i++){
            double[] previous=vertices.get(Math.floorMod(i-1,count)),current=vertices.get(i),next=vertices.get((i+1)%count);
            double inX=Math.signum(current[0]-previous[0]),inZ=Math.signum(current[1]-previous[1]);
            double outX=Math.signum(next[0]-current[0]),outZ=Math.signum(next[1]-current[1]);
            double safe=Math.min(radius,Math.min(Math.hypot(current[0]-previous[0],current[1]-previous[1]),Math.hypot(next[0]-current[0],next[1]-current[1]))*.32);
            entry[i][0]=current[0]-inX*safe;entry[i][1]=current[1]-inZ*safe;
            exit[i][0]=current[0]+outX*safe;exit[i][1]=current[1]+outZ*safe;
        }
        points.add(exit[0]);
        for(int step=1;step<=count;step++){
            int i=step%count;double[] current=vertices.get(i);
            double[] from=points.get(points.size()-1);line(points,from[0],from[1],entry[i][0],entry[i][1]);
            double[] previous=vertices.get(Math.floorMod(i-1,count)),next=vertices.get((i+1)%count);
            double inX=Math.signum(current[0]-previous[0]),inZ=Math.signum(current[1]-previous[1]);
            double outX=Math.signum(next[0]-current[0]),outZ=Math.signum(next[1]-current[1]);
            double safe=Math.hypot(entry[i][0]-current[0],entry[i][1]-current[1]);
            double centerX=entry[i][0]+outX*safe,centerZ=entry[i][1]+outZ*safe;
            double startAngle=Math.atan2(entry[i][1]-centerZ,entry[i][0]-centerX),turn=Math.signum(inX*outZ-inZ*outX);
            int curveCount=Math.max(2,(int)Math.ceil(Math.PI*safe*.5/SAMPLE_SPACING));
            for(int j=1;j<=curveCount;j++){double angle=startAngle+turn*Math.PI*.5*j/curveCount;points.add(new double[]{centerX+Math.cos(angle)*safe,centerZ+Math.sin(angle)*safe});}
        }
    }

    /** Re-indexes the loop to the middle of the straight nearest the podium at positive Z. */
    private static void rotateStartToPodiumStraight(List<double[]> points,double centerX){
        if(points.size()<4)return;int unique=points.size()-1;double maxZ=points.subList(0,unique).stream().mapToDouble(p->p[1]).max().orElse(0);int best=-1;double score=Double.MAX_VALUE;
        for(int i=0;i<unique;i++){double[] before=points.get(Math.floorMod(i-1,unique)),p=points.get(i),after=points.get((i+1)%unique);double cross=(p[0]-before[0])*(after[1]-p[1])-(p[1]-before[1])*(after[0]-p[0]);if(p[1]<maxZ-.05||Math.abs(cross)>.001)continue;double candidate=Math.abs(p[0]-centerX);if(candidate<score){score=candidate;best=i;}}
        if(best<0)return;List<double[]> rotated=new ArrayList<>(points.size());for(int i=0;i<unique;i++)rotated.add(points.get((best+i)%unique));rotated.add(rotated.getFirst());points.clear();points.addAll(rotated);
    }

    private static TurtleSurface surfaceAt(double distance, double length, boolean beach) {
        if(isStartFinishZone(distance,length))return beach?TurtleSurface.SAND:TurtleSurface.MUD;
        if(waterAt(distance,length))return TurtleSurface.PUDDLE;
        return beach?TurtleSurface.SAND:TurtleSurface.MUD;
    }

    /** Two broad, predictable swimming sections totaling 44% of the lap. */
    private static boolean waterAt(double distance,double length){
        if(isStartFinishZone(distance,length)||length<=0)return false;
        double ratio=distance/length;
        return ratio>=.18&&ratio<.40||ratio>=.52&&ratio<.74;
    }

    private static boolean isStartFinishZone(double distance,double length){
        return distance<=START_FINISH_SAFE_DISTANCE||length-distance<=START_FINISH_SAFE_DISTANCE;
    }

    public Sample sampleAt(double progress) {
        double clamped=Math.max(0,Math.min(length,progress));
        if(clamped<=0)return samples.getFirst();
        if(clamped>=length)return samples.getLast();
        int low=0,high=samples.size()-1;
        while(low+1<high){int middle=(low+high)>>>1;if(samples.get(middle).distance<clamped)low=middle;else high=middle;}
        Sample a=samples.get(low),b=samples.get(high);
        double span=Math.max(1.0e-8,b.distance-a.distance),t=Math.max(0,Math.min(1,(clamped-a.distance)/span));
        double tx=a.tangentX+(b.tangentX-a.tangentX)*t,tz=a.tangentZ+(b.tangentZ-a.tangentZ)*t;
        double magnitude=Math.max(1.0e-8,Math.hypot(tx,tz));tx/=magnitude;tz/=magnitude;
        TurtleSurface surface=isStartFinishZone(clamped)?startFinishSurface():(t<.5?a.surface:b.surface);
        return new Sample(clamped,a.x+(b.x-a.x)*t,a.z+(b.z-a.z)*t,tx,tz,-tz,tx,
                a.curvature+(b.curvature-a.curvature)*t,a.turnSign+(b.turnSign-a.turnSign)*t,
                surface);
    }

    /**
     * Maps logical progress to a continuous offset curve. The old normal-vector-only mapping could
     * fold an inner lane backwards at a tight 90-degree corner even while logical progress advanced.
     * The finite-difference tangent below also keeps the rendered turtle facing along its own lane.
     */
    public TrackPoint trackPoint(double progress,double laneOffset){
        double clamped=Math.max(0,Math.min(length,progress));
        double lanePosition=Math.max(0,Math.min(7,laneOffset/RaceEntry.LANE_SPACING+3.5));
        int lower=(int)Math.floor(lanePosition),upper=(int)Math.ceil(lanePosition);double blend=lanePosition-lower;
        double[] a=lanePoint(laneTracks.get(lower),clamped),b=lanePoint(laneTracks.get(upper),clamped);
        double x=a[0]+(b[0]-a[0])*blend,z=a[1]+(b[1]-a[1])*blend;
        double tx=a[2]+(b[2]-a[2])*blend,tz=a[3]+(b[3]-a[3])*blend,magnitude=Math.hypot(tx,tz);
        if(magnitude<1.0e-8){Sample sample=sampleAt(clamped);tx=sample.tangentX;tz=sample.tangentZ;}
        else{tx/=magnitude;tz/=magnitude;}
        return new TrackPoint(x,z,tx,tz,sampleAt(clamped).surface());
    }

    private static List<LaneTrack> buildLaneTracks(List<Sample> samples){
        List<LaneTrack> tracks=new ArrayList<>(8);
        for(int lane=0;lane<8;lane++){
            double offset=RaceEntry.laneCenterOffset(lane);List<double[]> points=new ArrayList<>(samples.size());double[] centerDistances=new double[samples.size()],actualDistances=new double[samples.size()];double total=0;
            for(int i=0;i<samples.size();i++){Sample sample=samples.get(i);double[] point={sample.x+sample.normalX*offset,sample.z+sample.normalZ*offset};if(i>0){double[] previous=points.get(i-1);total+=Math.hypot(point[0]-previous[0],point[1]-previous[1]);}points.add(point);centerDistances[i]=sample.distance;actualDistances[i]=total;}
            tracks.add(new LaneTrack(List.copyOf(points),centerDistances,actualDistances,total));
        }
        return List.copyOf(tracks);
    }

    private static double[] lanePoint(LaneTrack track,double progress){
        double target=Math.max(0,Math.min(track.centerDistances[track.centerDistances.length-1],progress));if(target<=0){double[] a=track.points.getFirst(),b=track.points.get(1);return pointAndTangent(a,b,0);}
        if(target>=track.centerDistances[track.centerDistances.length-1]){int last=track.points.size()-1;return pointAndTangent(track.points.get(last-1),track.points.get(last),1);}
        int low=0,high=track.centerDistances.length-1;while(low+1<high){int middle=(low+high)>>>1;if(track.centerDistances[middle]<target)low=middle;else high=middle;}
        double span=Math.max(1.0e-8,track.centerDistances[high]-track.centerDistances[low]),t=(target-track.centerDistances[low])/span;
        return pointAndTangent(track.points.get(low),track.points.get(high),t);
    }

    /** Actual lane metres needed for one metre of centre-line progress. */
    public double distanceScale(double progress,double laneOffset){double lanePosition=Math.max(0,Math.min(7,laneOffset/RaceEntry.LANE_SPACING+3.5));int lower=(int)Math.floor(lanePosition),upper=(int)Math.ceil(lanePosition);double blend=lanePosition-lower;return laneScale(laneTracks.get(lower),progress)*(1-blend)+laneScale(laneTracks.get(upper),progress)*blend;}
    private static double laneScale(LaneTrack track,double progress){double target=Math.max(0,Math.min(track.centerDistances[track.centerDistances.length-1],progress));int low=0,high=track.centerDistances.length-1;while(low+1<high){int middle=(low+high)>>>1;if(track.centerDistances[middle]<target)low=middle;else high=middle;}double center=Math.max(1.0e-8,track.centerDistances[high]-track.centerDistances[low]);return Math.max(.35,Math.min(1.65,(track.actualDistances[high]-track.actualDistances[low])/center));}

    private static double[] pointAndTangent(double[] a,double[] b,double t){
        double tx=b[0]-a[0],tz=b[1]-a[1],magnitude=Math.max(1.0e-8,Math.hypot(tx,tz));
        return new double[]{a[0]+tx*t,a[1]+tz*t,tx/magnitude,tz/magnitude};
    }

    /** Finished turtles may coast a few blocks past the line onto the opening straight. */
    public Sample sampleForWorld(double progress) {
        double visual=progress>length?progress-length:progress;
        return sampleAt(visual);
    }

    public TrackPoint trackPointForWorld(double progress,double laneOffset){
        double visual=progress>length?progress-length:progress;
        return trackPoint(visual,laneOffset);
    }

    /** Extrapolates behind the shared start/finish seam for the pre-race waiting line. */
    public TrackPoint startGridPoint(double progress,double laneOffset){
        if(progress>=0)return trackPointForWorld(progress,laneOffset);
        TrackPoint start=trackPoint(0,laneOffset);
        return new TrackPoint(start.x()+start.tangentX()*progress,start.z()+start.tangentZ()*progress,
                start.tangentX(),start.tangentZ(),startFinishSurface());
    }

    public List<Sample> samples() { return samples; }
    public double length() { return length; }
    public boolean beachTheme() { return beachTheme; }
    public long seed() { return seed; }
    /** Visual theme variant. Surface rules remain shared, so themes do not change race balance. */
    public String themeId(){return themeId;}
    public boolean neutralPhysics(){return neutralPhysics;}
    private static String defaultTheme(long seed,boolean beach){return beach?((seed&2)==0?"beach_festival":"mangrove_boardwalk"):((seed&2)==0?"forest_pond":"meadow_fair");}
    private static String normalizeTheme(String value,boolean beach){if(value==null)return defaultTheme(0,beach);return switch(value.toLowerCase(java.util.Locale.ROOT)){case "beach","beach_festival"->"beach_festival";case "mangrove","mangrove_boardwalk"->"mangrove_boardwalk";case "forest","forest_pond"->"forest_pond";case "meadow","meadow_fair"->"meadow_fair";default->defaultTheme(0,beach);};}
    public TurtleSurface startFinishSurface(){return samples.getFirst().surface();}
    public boolean isStartFinishZone(double distance){return isStartFinishZone(distance,length);}
}
