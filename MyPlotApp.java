package vizualizator3d.main;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.Pair;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * JavaFX 3D plot visualisation application
 * @author Katarína Osvaldová
 */
public class MyPlotApp extends Application {
    /**
     * Example functions
     */
    private static final BiFunction<Double, Double, Double> function1 = (x, y) -> 2.75/Math.exp(Math.pow(x/3,2)*Math.pow(y/3,2));
    private static final BiFunction<Double, Double, Double> function2 = (x, y) -> Math.sin(Math.sqrt(Math.abs(Math.pow(x, 2)+Math.pow(y, 2))));
    private static final BiFunction<Double, Double, Double> function3 = (x, y) -> 2*Math.sin(Math.sqrt(Math.abs(Math.pow(x/1.5,3)+Math.pow(y/1.5,3))));
    private static final BiFunction<Double, Double, Double> function4 = (x, y) -> -2*x*y*Math.exp(-Math.pow(x/4,2)-Math.pow(y/4,2));
    private static final BiFunction<Double, Double, Double> function5 = (x, y) -> .5*Math.cos(Math.abs(x)+Math.abs(y))*(Math.abs(x)+Math.abs(y));
    /**
     * Map of example functions with their names
     */
    Map<String, BiFunction<Double, Double, Double>> functionMap = new TreeMap<>(Map.of("Function1", function1,
                                                                                       "Function2", function2,
                                                                                       "Function3", function3,
                                                                                       "Function4", function4,
                                                                                       "Function5", function5));
    /**
     * Map of point-clouds with their names
     */
    Map<String, String> cloudMap = new HashMap<>(Map.of("Rabbit", "rabbit.xyz",
                                                        "Turtle Shell", "turtle.xyz",
                                                        "Benchy", "benchy.xyz",
                                                        "Sphere", "sphere.xyz",
                                                        "Teapot", "teapot.xyz",
                                                        "Helix", "helix.xyz"));
    /**
     * Main graphic area
     */
    private final Group group = new Group();
    private final SubScene scene = new SubScene(group, 1100, 700, true, SceneAntialiasing.BALANCED);
    private final BorderPane layout = new BorderPane();
    private final Scene root = new Scene(layout, 1280, 720);
    private AmbientLight ambientLight;
    private ColorPicker minColourPicker;
    /**
     * UI objects
     */
    private final VBox rightPanel = new VBox();
    private final VBox leftPanel = new VBox();
    private final HBox UIPanel = new HBox(leftPanel, rightPanel);
    private ColorPicker maxColourPicker;
    private Slider resolutionSlider;
    private Slider xMinPrecisionSlider;
    private Slider yMinPrecisionSlider;
    private Slider xMinSlider;
    private Slider yMinSlider;
    private Slider zUnitSlider;
    /**
     * Multiplier for coordinates, allowing visual tweaking
     */
    private final int spread = 2;
    /**
     * Default radius of displayed spheres
     */
    private final double valueBallRadius = .1;
    /**
     * Maps of value-spheres, value changes for animated transition
     */
    private Map<Pair<Double, Double>, Sphere> functionValuePoints;
    private Map<Pair<Double, Double>, Double> functionValueChange;
    /**
     * Map of materials for value-spheres
     * The map contains 101 colours linearly interpolated between min/max-Colours
     */
    private Map<String, PhongMaterial> colourMaterialMap;
    /**
     * Parameters and default values for UI control elements -> colourPickers
     */
    private final Color defaultMinColour = Color.LAWNGREEN;
    private final Color defaultMaxColour = Color.ORANGERED;
    /**
     * Parameters and default values for UI control elements -> x/y-limits
     */
    private final double defaultLowerLimit = -10;
    private final double minLimit = -500;
    private final double maxLimit = 500;
    private final double length = 20;
    /**
     * Parameters and default values for UI control elements -> resolution of plots (interval between values on x and y axes)
     */
    private final double maxResolution = .55;
    private final double minResolution = .05;
    private final double minorResolutionTicks = .01;
    private final double majorResolutionTicks = .1;
    private final double defaultResolution = .15;
    /**
     * Parameters and default values for UI control elements -> z axis zoom
     */
    private final double minZUnit = -2;
    private final double maxZUnit = 2;
    private final double defaultZUnit = 0;
    private double currentZoom = Math.pow(10, defaultZUnit);
    private double anchorX, anchorY, anchorAngleX, anchorAngleY;
    /**
     * Number of steps in transition animation for value changes
     */
    private int animationStepCount = 100;
    /**
     * Duration of transition animation for value changes
     */
    private final double animationDuration = 2;
    /**
     * min/max-values of currently displayed values used mainly for colour interpolation
     */
    private double minValue = 0;
    private double maxValue = 0;
    /**
     * minValue - maxValue
     */
    private double minMaxDifference;
    /**
     * Currently displayed function
     */
    BiFunction<Double, Double, Double> currentFunction;
    /**
     * Logical value, true if a point-cloud is displayed, false if a plot is displayed
     */
    boolean displayingXYZ;
    /**
     * Boundaries for scrolling
     */
    private final double maxDistance = 300;
    private final double minDistance = -70;

    /**
     * Starts the application;
     * -> creates UI sidebar;
     * -> creates and positions the camera;
     * -> sets up the main scene for the actual plotting;
     * -> add light to the scene;
     * -> creates colours for value-spheres;
     * -> initializes mouse controls;
     * -> creates blank plot;
     * -> displays function1;
     * @param primaryStage primaryStage of the application
     */
    @Override
    public void start(Stage primaryStage) {
        layout.setCenter(scene);
        layout.setLeft(UIPanel);

        Camera camera = new PerspectiveCamera(true);
        camera.getTransforms().add(new Rotate(-100, Rotate.X_AXIS));
        camera.setTranslateY(-85);
        camera.setTranslateZ(15);
        camera.setTranslateX(5);
        camera.setFarClip(maxDistance + 100);
        scene.setCamera(camera);

        scene.setFill(Color.GHOSTWHITE);
        scene.widthProperty().bind(root.widthProperty());
        scene.heightProperty().bind(root.heightProperty().subtract(rightPanel.getMinHeight()));

        ambientLight = new AmbientLight(Color.WHITE);
        group.getChildren().add(ambientLight);

        prepareControlPanel();
        createMaterialColourMap();
        initiateMouseControl(group, scene);
        initialDisplay();
        changeFunction(function1);

        primaryStage.setScene(root);
        primaryStage.show();
        primaryStage.setTitle("3D plots!");
    }

    /**
     * Creates map for colours, and fills is with default colours
     */
    private void createMaterialColourMap() {
        colourMaterialMap = new HashMap<>();
        assignColours();
    }

    /**
     * Creates 101 colours interpolated between colours chosen/stored in colourPickers in UI sidebar;
     * The map in reality contains PhongMaterials with said colours (keys are fraction usd to in the interpolation)
     * The reason why materials are stored, is that when the ColourPicker changes colour, the spheres used to display
     * point-clouds change colour, as they have these materials as their own;
     * The value-spheres of plots have their own Materials, ang change colours based on the ones stored here
     */
    private void assignColours() {
        if (colourMaterialMap.isEmpty()) {
            for (double i = 0; i < 1.01; i += .01) {
                colourMaterialMap.put(roundTo2Decimals(i), new PhongMaterial(maxColourPicker.getValue().interpolate(minColourPicker.getValue(), i)));
            }
        } else {
            for (double i = 0; i < 1.01; i += .01) {
                colourMaterialMap.get(roundTo2Decimals(i)).setDiffuseColor(maxColourPicker.getValue().interpolate(minColourPicker.getValue(), i));
            }
        }
    }

    /**
     * If a plot has not been displayed, this method cleans the stage, creates axes and creates value-spheres
     */
    private void initialDisplay()  {
        group.getChildren().clear();
        group.getChildren().add(ambientLight);
        displayingXYZ = false;
        displayAxes();
        initialDisplayValuePoints();
    }

    /**
     * Simply displaying x, y and z axes based on sphere radius and interval length of each axis
     */
    private void displayAxes() {
        double longSide = (length + 2) * spread;
        double shortSide = valueBallRadius;
        PhongMaterial blackMaterial = new PhongMaterial(Color.BLACK);
        Box axis;
        axis = new Box(shortSide, shortSide, longSide);
        axis.setMaterial(blackMaterial);
        group.getChildren().add(axis);
        axis = new Box(shortSide, longSide, shortSide);
        axis.setMaterial(blackMaterial);
        group.getChildren().add(axis);
        axis = new Box(longSide, shortSide, shortSide);
        axis.setMaterial(blackMaterial);
        group.getChildren().add(axis);
    }

    /**
     * Read and display point-cloud from provided file in .xyz format
     * @param filename filename of desired .xyz file to be displayed
     */
    public void displayXYZ(String filename) {
        displayingXYZ = true;
        group.getChildren().clear();
        group.getChildren().add(ambientLight);

        try (Stream<String> linesStream = Files.lines(new File(filename).toPath())) {
            boolean normalized = !(filename.equals("teapot.xyz") || filename.equals("helix.xyz"));
            linesStream.forEach(line -> {
                Matcher matcher = Pattern.compile("\\s*(-?\\d+\\.\\d*E?-?\\+?\\d*),?\\s*(-?\\d+\\.\\d*E?-?\\+?\\d*),?\\s*(-?\\d+\\.\\d*E?-?\\+?\\d*)\\s*")
                                         .matcher(line);
                if (matcher.matches()) {
                    double x = Double.parseDouble(matcher.group(1));
                    double y = Double.parseDouble(matcher.group(2));
                    double z = Double.parseDouble(matcher.group(3));
                    Sphere valuePoint = new Sphere(valueBallRadius * ((normalized) ? 1 : 3));
                    valuePoint.setTranslateX(x*spread*((normalized) ? 10 : 2));
                    valuePoint.setTranslateY(y*spread*((normalized) ? 10 : 2));
                    valuePoint.setTranslateZ((z-((normalized) ? 0 : 2))
                                             *spread*((normalized) ? 10 : 2));
                    valuePoint.setMaterial(colourMaterialMap.get("1.00"));
                    group.getChildren().add(valuePoint);
                }
            });
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Creation of value-spheres for plots;
     * The spheres are stored in a map, functionValuePoints, by their x and y coordinates for further manipulation
     */
    private void initialDisplayValuePoints()  {
        double resolution = resolutionSlider.getValue();
        double xMinLimit = defaultLowerLimit;
        double xMaxLimit = xMinLimit + length;
        double yMinLimit = defaultLowerLimit;
        double yMaxLimit = yMinLimit + length;
        functionValuePoints = new HashMap<>();
        functionValueChange = new HashMap<>();
        Sphere valuePoint;
        for (double x = xMinLimit; x <= xMaxLimit; x += resolution) {
            for (double y = yMinLimit; y <= yMaxLimit; y += resolution) {
                valuePoint = new Sphere(valueBallRadius);
                valuePoint.setTranslateX(x*spread);
                valuePoint.setTranslateY(y*spread);
                valuePoint.setMaterial(new PhongMaterial());
                functionValuePoints.put(new Pair<>(x, y), valuePoint);
            }
        }
        group.getChildren().addAll(functionValuePoints.values());
    }

    /**
     * Changes displayed function;
     * If there is no plot to just display the values, one is created
     * @param f binary function to be displayed
     */
    public void changeFunction(BiFunction<Double, Double, Double> f) {
        currentFunction = f;
        if (displayingXYZ) {
            initialDisplay();
        }
        calculateNewFunctionValues();
        animate(.3, true);
    }

    /**
     * Calculates values, by which the value-spheres are to move along the z-axis during animation transition;
     * The difference from calculateNewFunctionValues is the lack of the need to calculate the values and monitor the limits on the z-axis
     *
     * @param newZoom desired multiplier for values on the z axis
     */
    private void calculateZoomValues(double newZoom) {
        functionValueChange = new HashMap<>();
        double multiplicationFactor = newZoom/currentZoom;
        currentZoom = newZoom;

        for (Pair<Double, Double> valueCoords : functionValuePoints.keySet()) {
            functionValueChange.put(valueCoords,
                                    functionValuePoints.get(valueCoords).getTranslateZ() *
                                                            (multiplicationFactor - 1) /
                                                            animationStepCount);
        }
    }

    /**
     * Recalculate the function values and the increments for animation;
     * The function gets offset values, so that no matter what interval it's displaying, it's always displayed at the same coordinates
     */
    private void calculateNewFunctionValues() {
        boolean first = true;
        double xOffset = xMinSlider.getValue()*10 + xMinPrecisionSlider.getValue();
        double yOffset = yMinSlider.getValue()*10 + yMinPrecisionSlider.getValue();

        for (Pair<Double, Double> valueCoords : functionValuePoints.keySet()) {
            double value = currentFunction.apply(valueCoords.getKey()-xOffset,
                                                 valueCoords.getValue()-yOffset) * currentZoom;
            if (first) {
                first = false;
                minValue = value;
                maxValue = value;
            } else {
                if (maxValue < value) {
                    maxValue = value;
                } else if (minValue > value) {
                    minValue = value;
                }
            }
            functionValueChange.put(valueCoords,
                                    (value - functionValuePoints.get(valueCoords).getTranslateZ()) / animationStepCount
                                   );
        }
        minMaxDifference = (minValue - maxValue);
    }

    /**
     * Function to get a string of the number rounded to 2 decimals
     * @param number number to round
     * @return string of the rounded number with 2 decimal places
     */
    private static String roundTo2Decimals(double number) {
        return String.format("%.2f", number);
    }

    /**
     * Returns the factor, alpha, for which the value is the result of interpolation between minValue and maxValue
     * @param value value == alpha * minValue + (1-alpha) * maxValue
     * @return alpha
     */
    private double getInterpolationFraction(double value) {
        double fraction = (value-maxValue) / minMaxDifference;
        return (fraction >= 1) ? 1 : ((fraction <= 0) ? 0 : fraction);
    }

    /**
     * Appropriately changes colours of value-spheres of a displayed plot
     */
    private void recolour() {
        for (Pair<Double, Double> valueCoords : functionValuePoints.keySet()) {
            ((PhongMaterial)functionValuePoints.get(valueCoords)
                    .getMaterial()).setDiffuseColor(colourMaterialMap.getOrDefault(roundTo2Decimals(
                                                                    getInterpolationFraction(
                                                                        functionValuePoints.get(valueCoords).getTranslateZ())),
                                                                colourMaterialMap.get("0.00")).getDiffuseColor());
        }
    }

    /**
     * Initializes animation of transitions to new function values
     * @param delay seconds by which to delay the animation start
     * @param recolour true if the change of colour during the animation is desired
     */
    private void animate(double delay, boolean recolour) {
        Timeline animation = new Timeline(new KeyFrame(Duration.seconds(animationDuration/animationStepCount),
                                                       e -> animationStep(recolour)));
        animation.setCycleCount(animationStepCount);
        animation.setDelay(Duration.seconds(delay));
        animation.play();
    }

    /**
     * One tick of the animation;
     * each value-sphere is moved and recoloured if desired
     * @param recolour true if the change of colour during the animation is desired
     */
    private void animationStep(boolean recolour) {
        for (Pair<Double, Double> valueCoords : functionValuePoints.keySet()) {
            Sphere valueSphere = functionValuePoints.get(valueCoords);
            double newValue = functionValuePoints.get(valueCoords).getTranslateZ() +
                              functionValueChange.get(valueCoords);
            valueSphere.setTranslateZ(newValue);
            if (recolour) {
                ((PhongMaterial)valueSphere.getMaterial()).setDiffuseColor(colourMaterialMap.getOrDefault(roundTo2Decimals(
                                                                           getInterpolationFraction(newValue)),
                                                                       colourMaterialMap.get("0.00")).getDiffuseColor());
            }
        }
    }

    /**
     * Binds mouse control to target scene and group to move;
     * On drag, the group is rotated;
     * On scroll, the group is moved to/from the camera (within boundaries)
     * @param group target group to move
     * @param scene target scene to work on
     */
    private void initiateMouseControl(Group group, SubScene scene) {
        Rotate xRotate = new Rotate(25, Rotate.X_AXIS);
        Rotate yRotate = new Rotate(0, Rotate.Y_AXIS);
        group.getTransforms().addAll(xRotate, yRotate);

        scene.setOnMousePressed(event -> {anchorX = event.getSceneX();
                                          anchorY = event.getSceneY();
                                          anchorAngleX = xRotate.getAngle();
                                          anchorAngleY = yRotate.getAngle();
                                         });
        scene.setOnMouseDragged(event -> {xRotate.setAngle(anchorAngleX - (anchorY - event.getSceneY())/2);
                                          yRotate.setAngle(anchorAngleY - (anchorX - event.getSceneX())/2);
                                         });
        scene.setOnScroll(event -> {double newY = group.getTranslateY() + event.getDeltaY();
                                    if (newY < maxDistance && newY > minDistance) {
                                        group.translateYProperty().set(newY);
                                    }
                                   });
    }

    /**
     * Sets up the UI control sidebar;
     * Sets the style;
     * Adds buttons for various functions and point-clouds;
     * Adds sliders for plot manipulation;
     * Adds colourPickers for choice of colours for the plot
     */
    private void prepareControlPanel() {
        rightPanel.setMinWidth(120);
        rightPanel.setMinHeight(500);
        rightPanel.setStyle("-fx-background-color: LightGrey;");
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setSpacing(5);
        leftPanel.setSpacing(20);
        leftPanel.setMinWidth(30);
        leftPanel.setStyle("-fx-background-color: LightGrey;");
        leftPanel.setAlignment(Pos.CENTER);
        addFunctionButtons();
        addSliders();
        addColourPickers();
    }

    /**
     * Adds buttons to the UI control sidebars left part based on functionMap and cloudMap
     */
    private void addFunctionButtons() {
        leftPanel.getChildren().add(new Text("Functions:"));
        for (String functionName : functionMap.keySet()) {
            Button button = new Button(functionName);
            button.setOnAction(e -> changeFunction(functionMap.get(functionName)));
            button.setMaxWidth(80);
            button.setMinWidth(80);
            leftPanel.getChildren().add(button);
        }

        leftPanel.getChildren().add(new Text("Point clouds:"));
        for (String filename : cloudMap.keySet()) {
            Button button = new Button(filename);
            button.setOnAction(e -> displayXYZ(cloudMap.get(filename)));
            button.setMaxWidth(80);
            button.setMinWidth(80);
            leftPanel.getChildren().add(button);
        }

    }

    /**
     * Creation, placement to the right part of the UI sidebar and styling of a slider control element
     * @param title test to be displayed above
     * @param min lower limit for the slider
     * @param max upper limit for the slider
     * @param defaultValue default value
     * @param majorTicks length of the interval between the major ticks
     * @param minorTicks length of the interval between the minor ticks
     * @param snap true if the slider is to snap to a ticks on release
     * @return created Slider
     */
    private Slider addSlider(String title, double min, double max, double defaultValue, double majorTicks, double minorTicks, boolean snap) {
        Slider slider = new Slider(min, max, defaultValue);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(majorTicks);
        slider.setMinorTickCount((int)(majorTicks/minorTicks) - 1);
        slider.setBlockIncrement(0.1f);
        slider.setSnapToTicks(snap);
        slider.setPadding(new Insets(3,10,15,10));
        rightPanel.getChildren().add(new Text(title));
        rightPanel.getChildren().add(slider);
        return slider;
    }

    /**
     * Creation of desired sliders and assignment of their functions
     */
    private void addSliders() {
        EventHandler<Event> recalculate = e -> {calculateNewFunctionValues();
            animate(.5, true);};
        zUnitSlider = addSlider("log10(z axis zoom)", minZUnit, maxZUnit, defaultZUnit, (maxZUnit-minZUnit)/10, (maxZUnit-minZUnit)/10, false);
        zUnitSlider.setOnMouseReleased(e -> {calculateZoomValues(Math.pow(10, zUnitSlider.getValue()));
                                             animate(.5, false);});

        xMinSlider = addSlider("(x axis offset)*10", minLimit/10, maxLimit/10, 0,(maxLimit/10-minLimit/10)/10, 1, true);
        xMinSlider.setOnMouseReleased(recalculate);
        xMinPrecisionSlider = addSlider("x axis offset", -10, 10, 0,10, 1, true);
        xMinPrecisionSlider.setOnMouseReleased(recalculate);

        yMinSlider = addSlider("(y axis offset)*10", minLimit/10, maxLimit/10, 0,(maxLimit/10-minLimit/10)/10, 1, true);
        yMinSlider.setOnMouseReleased(recalculate);
        yMinPrecisionSlider = addSlider("y axis offset", -10, 10, 0,10,1, true);
        yMinPrecisionSlider.setOnMouseReleased(recalculate);

        resolutionSlider = addSlider("Resolution", minResolution, maxResolution, defaultResolution, majorResolutionTicks, minorResolutionTicks, true);
        resolutionSlider.setOnMouseReleased(e -> {if (resolutionSlider.getValue() < .15) {
                                                      animationStepCount = 3;
                                                  } else {
                                                      animationStepCount = 100;
                                                  }
                                                      group.getChildren().clear();
                                                      group.getChildren().add(ambientLight);
                                                      initialDisplay();
                                                      calculateNewFunctionValues();
                                                      animate(.3, true);
                                                 });
    }

    /**
     * Creation of colourPickers for choosing colours
     */
    private void addColourPickers() {
        EventHandler<ActionEvent> changeColours = e -> {assignColours();
            recolour();
        };
        minColourPicker = new ColorPicker(defaultMinColour);
        minColourPicker.setOnAction(changeColours);
        maxColourPicker = new ColorPicker(defaultMaxColour);
        maxColourPicker.setOnAction(changeColours);
        rightPanel.getChildren().addAll(new Text("Min value colour"), maxColourPicker,
                new Text("Max value colour"), minColourPicker);
    }

    /**
     * The main function launching the application
     * @param args
     */
    public static void main(String[] args) {
        launch();
    }
}