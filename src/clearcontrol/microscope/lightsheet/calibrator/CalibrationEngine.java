package clearcontrol.microscope.lightsheet.calibrator;

import static java.lang.Math.pow;

import java.io.File;
import java.util.HashMap;

import clearcontrol.core.configuration.MachineConfiguration;
import clearcontrol.core.device.task.TaskDevice;
import clearcontrol.core.log.LoggingFeature;
import clearcontrol.core.variable.Variable;
import clearcontrol.gui.jfx.custom.visualconsole.VisualConsoleInterface;
import clearcontrol.microscope.lightsheet.LightSheetMicroscope;
import clearcontrol.microscope.lightsheet.calibrator.modules.impl.CalibrationA;
import clearcontrol.microscope.lightsheet.calibrator.modules.impl.CalibrationHP;
import clearcontrol.microscope.lightsheet.calibrator.modules.impl.CalibrationP;
import clearcontrol.microscope.lightsheet.calibrator.modules.impl.CalibrationW;
import clearcontrol.microscope.lightsheet.calibrator.modules.impl.CalibrationWP;
import clearcontrol.microscope.lightsheet.calibrator.modules.impl.CalibrationXY;
import clearcontrol.microscope.lightsheet.calibrator.modules.impl.CalibrationZ;
import clearcontrol.microscope.lightsheet.component.detection.DetectionArmInterface;
import clearcontrol.microscope.lightsheet.component.lightsheet.LightSheetInterface;
import clearcontrol.scripting.engine.ScriptingEngine;

import org.ejml.simple.SimpleMatrix;

/**
 * Calibration engine
 *
 * @author royer
 */
public class CalibrationEngine extends TaskDevice implements
                               LoggingFeature,
                               VisualConsoleInterface
{

  private static final int cMaxIterations = 3;

  private File mCalibrationFolder =
                                  MachineConfiguration.get()
                                                      .getFolder("Calibration");

  private final LightSheetMicroscope mLightSheetMicroscope;
  private CalibrationZ mCalibrationZ;
  private CalibrationA mCalibrationA;
  private CalibrationXY mCalibrationXY;
  private CalibrationP mCalibrationP;
  private CalibrationW mCalibrationW;
  private CalibrationHP mCalibrationHP;
  private CalibrationWP mCalibrationWP;

  private HashMap<String, LightSheetPositioner> mPositionersMap =
                                                                new HashMap<>();

  @SuppressWarnings("unused")
  private int mNumberOfDetectionArmDevices;
  private int mNumberOfLightSheetDevices;

  private final Variable<Boolean>[] mCalibrateLightSheetOnOff;

  private final Variable<Boolean> mCalibrateZVariable =
                                                      new Variable<Boolean>("CalibrateZ",
                                                                            true);
  private final Variable<Boolean> mCalibrateAVariable =
                                                      new Variable<Boolean>("CalibrateA",
                                                                            false);
  private final Variable<Boolean> mCalibrateXYVariable =
                                                       new Variable<Boolean>("CalibrateXY",
                                                                             false);

  private final Variable<Boolean> mCalibratePVariable =
                                                      new Variable<Boolean>("CalibrateP",
                                                                            false);
  private final Variable<Boolean> mCalibrateWVariable =
                                                      new Variable<Boolean>("CalibrateW",
                                                                            false);
  private final Variable<Boolean> mCalibrateWPVariable =
                                                       new Variable<Boolean>("CalibrateWP",
                                                                             false);
  private final Variable<Boolean> mCalibrateHPVariable =
                                                       new Variable<Boolean>("CalibrateHP",
                                                                             false);

  private final Variable<String> mCalibrationDataName =
                                                      new Variable<String>("CalibrationName",
                                                                           "system");

  private final Variable<Double> mProgressVariable;

  /**
   * Instantiates a calibrator, given a lightsheet microscope
   * 
   * @param pLightSheetMicroscope
   *          lightsheet microscope
   */
  @SuppressWarnings("unchecked")
  public CalibrationEngine(LightSheetMicroscope pLightSheetMicroscope)
  {
    super("Calibrator");

    mLightSheetMicroscope = pLightSheetMicroscope;
    mCalibrationZ = new CalibrationZ(this);
    mCalibrationA = new CalibrationA(this);
    mCalibrationXY = new CalibrationXY(this);
    mCalibrationP = new CalibrationP(this);
    mCalibrationW = new CalibrationW(this);
    mCalibrationWP = new CalibrationWP(this);
    mCalibrationHP = new CalibrationHP(this);

    mNumberOfDetectionArmDevices =
                                 mLightSheetMicroscope.getDeviceLists()
                                                      .getNumberOfDevices(DetectionArmInterface.class);

    mNumberOfLightSheetDevices =
                               mLightSheetMicroscope.getDeviceLists()
                                                    .getNumberOfDevices(LightSheetInterface.class);

    mProgressVariable = new Variable<Double>(getName() + "Progress",
                                             0.0);

    mCalibrateLightSheetOnOff =
                              new Variable[mNumberOfLightSheetDevices];
    for (int l = 0; l < mNumberOfLightSheetDevices; l++)
    {
      mCalibrateLightSheetOnOff[l] =
                                   new Variable<Boolean>("CalibrateLightSheet"
                                                         + l, true);
    }

  }

  /**
   * Returns a lightsheet microscope
   * 
   * @return lightsheet microscope
   */
  public LightSheetMicroscope getLightSheetMicroscope()
  {
    return mLightSheetMicroscope;
  }

  @Override
  public boolean startTask()
  {
    if (getLightSheetMicroscope().getCurrentTask().get() != null)
    {
      warning("Another task (%s) is already running, please stop it first.",
              getLightSheetMicroscope().getCurrentTask());
      return false;
    }
    getLightSheetMicroscope().getCurrentTask().set(this);
    return super.startTask();
  }

  @Override
  public void run()
  {
    try
    {
      mProgressVariable.set(0.0);
      calibrate();
      mProgressVariable.set(1.0);
      info("############################################## Calibration done");
    }
    finally
    {
      getLightSheetMicroscope().getCurrentTask().set(null);
    }
  }

  /**
   * Performs calibration
   * 
   * @return true if foinished normally, false if calibration was canceled or
   *         failed
   */
  public boolean calibrate()
  {

    int lNumberOfSamplesZ = 13;

    if (getCalibrateZVariable().get()
        && !calibrateZ(lNumberOfSamplesZ))
      return false;

    if (isStopRequested())
      return false;/**/

    if (getCalibrateAVariable().get() && !calibrateA(32, 4))
      return false;

    if (isStopRequested())
      return false;/**/

    if (getCalibrateXYVariable().get() && !calibrateXY(3))
      return false;

    if (isStopRequested())
      return false;/**/

    if (getCalibratePVariable().get() && !calibrateP())
      return false;

    if (isStopRequested())
      return false;/**/

    /*if (!calibrateW(32))
    	return false;/**/

    if ((getCalibrateAVariable().get()
         || getCalibrateXYVariable().get())
        && getCalibrateZVariable().get()
        && !calibrateZ(lNumberOfSamplesZ))
      return false;

    if (isStopRequested())
      return false;/**/

    if (getCalibratePVariable().get() && !calibrateP())
      if (isStopRequested())
        return false;/**/

    return true;
  }

  /**
   * Returns true if calibration should be stopped immediately.
   * 
   * @return true for stopping, false otherwise.
   */
  public boolean isStopRequested()
  {
    return ScriptingEngine.isCancelRequestedStatic() || !isRunning()
           || getStopSignalVariable().get();
  }

  /**
   * Calibrates the lightsheet and detection arm Z positions.
   * 
   * @param pNumberOfSamples
   *          number of samples
   * @return true when succeeded
   */
  public boolean calibrateZ(int pNumberOfSamples)
  {
    for (int l = 0; l < mNumberOfLightSheetDevices
                    && !isStopRequested(); l++)
      if (getCalibrateLightSheetOnOff(l).get())
      {
        int lIteration = 0;
        double lError = Double.POSITIVE_INFINITY;
        do
        {
          double lSearchAmplitude = 1.0 / (pow(2, 1 + lIteration));
          lError =
                 calibrateZ(l,
                            pNumberOfSamples,
                            pNumberOfSamples,
                            lIteration > 0,
                            lSearchAmplitude,
                            l == 0);
          info("############################################## Error = "
               + lError);
          if (ScriptingEngine.isCancelRequestedStatic()
              || !isRunning())
            return false;

        }
        while (lError >= 0.02 && lIteration++ < cMaxIterations
               && !isStopRequested());
        info("############################################## Done ");
        mProgressVariable.set((1.0 * l) / mNumberOfLightSheetDevices);
      }
    return true;
  }

  /**
   * Calibrates the alpha angle.
   * 
   * @param pNumberOfAngles
   *          number of angles
   * @param pNumberOfRepeats
   *          number of repeats
   * @return true when succeeded
   */
  public boolean calibrateA(int pNumberOfAngles, int pNumberOfRepeats)
  {
    for (int l = 0; l < mNumberOfLightSheetDevices
                    && !isStopRequested(); l++)
      if (getCalibrateLightSheetOnOff(l).get())
      {
        int lIteration = 0;
        double lError = Double.POSITIVE_INFINITY;
        do
        {
          lError = calibrateA(l, pNumberOfAngles, pNumberOfRepeats);
          info("############################################## Error = "
               + lError);
          if (ScriptingEngine.isCancelRequestedStatic()
              || !isRunning())
            return false;

        }
        while (lError >= 0.5 && lIteration++ < cMaxIterations
               && !isStopRequested());
        info("############################################## Done ");
        mProgressVariable.set((1.0 * l) / mNumberOfLightSheetDevices);
      }
    return true;
  }

  /**
   * Calibrates X and Y lighthseet positions
   * 
   * @param pNumberOfPoints
   *          number of points
   * @return true when succeeded
   */
  public boolean calibrateXY(int pNumberOfPoints)
  {
    for (int l = 0; l < mNumberOfLightSheetDevices
                    && !isStopRequested(); l++)
      if (getCalibrateLightSheetOnOff(l).get())
      {
        int lIteration = 0;
        double lError = Double.POSITIVE_INFINITY;
        do
        {
          lError = calibrateXY(l, 0, pNumberOfPoints);
          info("############################################## Error = "
               + lError);
          if (ScriptingEngine.isCancelRequestedStatic()
              || !isRunning())
            return false;

        }
        while (lError >= 0.05 && lIteration++ < cMaxIterations
               && !isStopRequested());
        info("############################################## Done ");
        mProgressVariable.set((1.0 * l) / mNumberOfLightSheetDevices);
      }

    return true;
  }

  /**
   * @return true when succeeded
   */
  public boolean calibrateP()
  {
    mCalibrationP.reset();
    int lIteration = 0;
    double lError = Double.POSITIVE_INFINITY;
    do
    {
      mCalibrationP.calibrate();
      lError = mCalibrationP.apply();

      info("############################################## Error = "
           + lError);
      if (ScriptingEngine.isCancelRequestedStatic() || !isRunning())
        return false;

      mProgressVariable.set((1.0 * lIteration) / cMaxIterations);
    }
    while (lError >= 0.04 && lIteration++ < cMaxIterations
           && !isStopRequested());
    info("############################################## Done ");

    return true;
  }

  /**
   * Calibrates the lighthseet laser power versus its height
   * 
   * @param pNumberOfSamplesH
   *          number of samples for the height
   * @param pNumberOfSamplesP
   *          number of samples for the laser power
   * @return true when succeeded
   */
  public boolean calibrateHP(int pNumberOfSamplesH,
                             int pNumberOfSamplesP)
  {
    for (int l = 0; l < mNumberOfLightSheetDevices
                    && !isStopRequested(); l++)
      if (getCalibrateLightSheetOnOff(l).get())
      {
        calibrateHP(l, 0, pNumberOfSamplesH, pNumberOfSamplesP);
        info("############################################## Done ");
        mProgressVariable.set((1.0 * l) / mNumberOfLightSheetDevices);
      }
    return true;
  }

  /**
   * Calibrates the width (beam NA) of the lighthsheet
   * 
   * @param pNumberOfSamples
   *          number of samples
   * @return true when succeeded
   */
  public boolean calibrateW(int pNumberOfSamples)
  {
    calibrateW(0, pNumberOfSamples);
    return true;
  }

  // /***************************************************************/ //

  /**
   * Calibrates the lightsheet and detection arms Z positions.
   * 
   * @param pLightSheetIndex
   *          lightsheet index
   * @param pNumberOfDSamples
   *          number of detection Z samples
   * @param pNumberOfISamples
   *          number of illumination Z samples
   * @param pRestrictedSearch
   *          true-> restrict search, false -> not
   * @param pSearchAmplitude
   *          search amplitude (within [0,1])
   * @param pAdjustDetectionZ
   *          true -> adjust detection Z
   * @return true when succeeded
   */
  public double calibrateZ(int pLightSheetIndex,
                           int pNumberOfDSamples,
                           int pNumberOfISamples,
                           boolean pRestrictedSearch,
                           double pSearchAmplitude,
                           boolean pAdjustDetectionZ)
  {
    mCalibrationZ.calibrate(pLightSheetIndex,
                            pNumberOfDSamples,
                            pNumberOfISamples,
                            pRestrictedSearch,
                            pSearchAmplitude);

    return mCalibrationZ.apply(pLightSheetIndex, pAdjustDetectionZ);
  }

  /**
   * Calibrates the lightsheet alpha angles.
   * 
   * @param pLightSheetIndex
   *          lightsheet index
   * @param pNumberOfAngles
   *          number of angles
   * @param pNumberOfRepeats
   *          number of repeats
   * @return true when succeeded
   */
  public double calibrateA(int pLightSheetIndex,
                           int pNumberOfAngles,
                           int pNumberOfRepeats)
  {
    mCalibrationA.calibrate(pLightSheetIndex,
                            pNumberOfAngles,
                            pNumberOfRepeats);

    return mCalibrationA.apply(pLightSheetIndex);
  }

  /**
   * Calibrates the XY position of the lighthsheets
   * 
   * @param pLightSheetIndex
   *          lightshet index
   * @param pDetectionArmIndex
   *          detection arm index
   * @param pNumberOfPoints
   *          number of points
   * @return true when succeeded
   */
  public double calibrateXY(int pLightSheetIndex,
                            int pDetectionArmIndex,
                            int pNumberOfPoints)
  {
    mCalibrationXY.calibrate(pLightSheetIndex,
                             pDetectionArmIndex,
                             pNumberOfPoints);

    return mCalibrationXY.apply(pLightSheetIndex, pDetectionArmIndex);
  }

  /**
   * Calibrates and the lightsheet width
   * 
   * @param pDetectionArmIndex
   *          detection arm index
   * @param pNumberOfSamples
   *          number of samples
   * @return true when succeeded
   */
  public double calibrateW(int pDetectionArmIndex,
                           int pNumberOfSamples)
  {
    mCalibrationW.calibrate(pDetectionArmIndex, pNumberOfSamples);

    return mCalibrationW.apply();
  }

  /**
   * Calibrates the lightsheet laser power versus its width
   * 
   * @param pLightSheetIndex
   *          lightsheet index
   * @param pDetectionArmIndex
   *          detection arm index
   * @param pNumberOfSamplesW
   *          number of samples for the width
   * @param pNumberOfSamplesP
   *          number of samples for the laser power
   * @return true when succeeded
   */
  public double calibrateWP(int pLightSheetIndex,
                            int pDetectionArmIndex,
                            int pNumberOfSamplesW,
                            int pNumberOfSamplesP)
  {
    mCalibrationWP.calibrate(pLightSheetIndex,
                             pDetectionArmIndex,
                             pNumberOfSamplesW,
                             pNumberOfSamplesP);

    return mCalibrationWP.apply(pLightSheetIndex, pDetectionArmIndex);
  }

  /**
   * Calibrates the lightsheet power versus its height
   * 
   * @param pLightSheetIndex
   *          lightsheet index
   * @param pDetectionArmIndex
   *          detection arm index
   * @param pNumberOfSamplesH
   *          number of samples for the height
   * @param pNumberOfSamplesP
   *          number of samples for the laser power
   * @return true when succeeded
   */
  public double calibrateHP(int pLightSheetIndex,
                            int pDetectionArmIndex,
                            int pNumberOfSamplesH,
                            int pNumberOfSamplesP)
  {
    mCalibrationHP.calibrate(pLightSheetIndex,
                             pDetectionArmIndex,
                             pNumberOfSamplesH,
                             pNumberOfSamplesP);

    return mCalibrationHP.apply(pLightSheetIndex, pDetectionArmIndex);
  }

  /**
   * Resets the calibration information
   */
  public void reset()
  {
    mCalibrationZ.reset();
    mCalibrationA.reset();
    mCalibrationXY.reset();
    mCalibrationP.reset();
    mCalibrationW.reset();

    final int lNumberOfDetectionArmDevices =
                                           mLightSheetMicroscope.getDeviceLists()
                                                                .getNumberOfDevices(DetectionArmInterface.class);

    for (int i = 0; i < lNumberOfDetectionArmDevices; i++)
    {
      final DetectionArmInterface lDetectionArmDevice =
                                                      mLightSheetMicroscope.getDeviceLists()
                                                                           .getDevice(DetectionArmInterface.class,
                                                                                      i);
      lDetectionArmDevice.resetFunctions();

    }

    final int lNumberOfLightSheetDevices =
                                         mLightSheetMicroscope.getDeviceLists()
                                                              .getNumberOfDevices(LightSheetInterface.class);

    for (int i = 0; i < lNumberOfLightSheetDevices; i++)
    {
      final LightSheetInterface lLightSheetDevice =
                                                  mLightSheetMicroscope.getDeviceLists()
                                                                       .getDevice(LightSheetInterface.class,
                                                                                  i);

      lLightSheetDevice.resetFunctions();

    }
  }

  /**
   * 
   */
  public void positioners()
  {

    final int lNumberOfLightSheetDevices =
                                         mLightSheetMicroscope.getDeviceLists()
                                                              .getNumberOfDevices(LightSheetInterface.class);

    final int lNumberOfDetectionArmDevices =
                                           mLightSheetMicroscope.getDeviceLists()
                                                                .getNumberOfDevices(DetectionArmInterface.class);

    for (int l = 0; l < lNumberOfLightSheetDevices; l++)
      for (int d = 0; d < lNumberOfDetectionArmDevices; d++)
      {

        SimpleMatrix lTransformMatrix =
                                      mCalibrationXY.getTransformMatrix(l,
                                                                        d);

        if (lTransformMatrix != null)
        {
          LightSheetPositioner lLightSheetPositioner =
                                                     new LightSheetPositioner(lTransformMatrix);

          setPositioner(l, d, lLightSheetPositioner);
        }
      }

  }

  /**
   * Sets the positioner to use for a given lightsheet and detection arm
   * 
   * @param pLightSheetIndex
   *          lightsheet index
   * @param pDetectionArmIndex
   *          detection arm index
   * @param pLightSheetPositioner
   *          lightsheet positioner
   */
  public void setPositioner(int pLightSheetIndex,
                            int pDetectionArmIndex,
                            LightSheetPositioner pLightSheetPositioner)
  {
    mPositionersMap.put("i" + pLightSheetIndex
                        + "d"
                        + pDetectionArmIndex,
                        pLightSheetPositioner);

  }

  /**
   * Returns the lightsheet positioner for a given lightsheet index and
   * detection arm index
   * 
   * @param pLightSheetIndex
   *          light sheet index
   * @param pDetectionArmIndex
   *          detection arm index
   * @return positioner
   */
  public LightSheetPositioner getPositioner(int pLightSheetIndex,
                                            int pDetectionArmIndex)
  {
    return mPositionersMap.get("i" + pLightSheetIndex
                               + "d"
                               + pDetectionArmIndex);

  }

  /**
   * Saves the calibration information to a file.
   */
  public void save()
  {
    save(mCalibrationDataName.get());
  }

  /**
   * Loads the calibration from a file
   * 
   * @return true -> success
   */
  public boolean load()
  {
    return load(mCalibrationDataName.get());
  }

  /**
   * Saves this calibration to a file of given name.
   * 
   * @param pName
   *          calibration name
   */
  public void save(String pName)
  {
    CalibrationData lCalibrationData =
                                     new CalibrationData(mLightSheetMicroscope);

    lCalibrationData.copyFrom(mLightSheetMicroscope);

    lCalibrationData.copyFrom(mPositionersMap);

    lCalibrationData.saveTo(getFile(pName));

  }

  /**
   * Loads calibration from a file of given name
   * 
   * @param pName
   *          name
   * @return true -> success
   */
  public boolean load(String pName)
  {
    File lFile = getFile(pName);

    if (!lFile.exists())
      return false;

    CalibrationData lCalibrationData =
                                     CalibrationData.readFrom(lFile);

    lCalibrationData.applyTo(mLightSheetMicroscope);

    lCalibrationData.copyTo(mPositionersMap);

    return true;
  }

  private File getFile(String pName)
  {
    return new File(mCalibrationFolder, pName + ".json");
  }

  /**
   * Returns the variable holding the 'calibrate Z' boolean flag.
   * 
   * @return calibrate Z variable
   */
  public Variable<Boolean> getCalibrateZVariable()
  {
    return mCalibrateZVariable;
  }

  /**
   * Returns the variable holding the 'calibrate A' boolean flag.
   * 
   * @return calibrate A variable
   */
  public Variable<Boolean> getCalibrateAVariable()
  {
    return mCalibrateAVariable;
  }

  /**
   * Returns the variable holding the 'calibrate XY' boolean flag.
   * 
   * @return calibrate XY variable
   */
  public Variable<Boolean> getCalibrateXYVariable()
  {
    return mCalibrateXYVariable;
  }

  /**
   * Returns the variable holding the 'calibrate P' boolean flag.
   * 
   * @return calibrate P variable
   */
  public Variable<Boolean> getCalibratePVariable()
  {
    return mCalibratePVariable;
  }

  /**
   * Returns the variable holding the 'calibrate W' boolean flag.
   * 
   * @return calibrate W variable
   */
  public Variable<Boolean> getCalibrateWVariable()
  {
    return mCalibrateWVariable;
  }

  /**
   * Returns the variable holding the 'calibrate WP' boolean flag.
   * 
   * @return calibrate WP variable
   */
  public Variable<Boolean> getCalibrateWPVariable()
  {
    return mCalibrateWPVariable;
  }

  /**
   * Returns the variable holding the 'calibrate HP' boolean flag.
   * 
   * @return calibrate HP variable
   */
  public Variable<Boolean> getCalibrateHPVariable()
  {
    return mCalibrateHPVariable;
  }

  /**
   * Returns the variable holding the 'calibrate W' boolean flag.
   * 
   * @return calibrate W variable
   */
  public Variable<Double> getProgressVariable()
  {
    return mProgressVariable;
  }

  /**
   * Returns the calibration data name variable
   * 
   * @return calibration data name variable
   */
  public Variable<String> getCalibrationDataNameVariable()
  {
    return mCalibrationDataName;
  }

  /**
   * Returns the variable holding the calibrate on/off flag. This flag decides
   * whether the lightsheet should be calibrated.
   * 
   * @param pLightSheetIndex
   *          lightsheet index
   * 
   * @return calibrate lightsheet variable
   */
  public Variable<Boolean> getCalibrateLightSheetOnOff(int pLightSheetIndex)
  {
    return mCalibrateLightSheetOnOff[pLightSheetIndex];
  }

  /**
   * Returns the variable holding the 'is-running' flag.
   * 
   * @return is-running variable
   */
  public boolean isRunning()
  {
    return getIsRunningVariable().get();
  }

}
