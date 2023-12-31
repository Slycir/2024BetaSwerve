// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import static frc.robot.Constants.MeasurementConstants.*;
import static frc.robot.Constants.CANConstants.*;

import com.kauailabs.navx.frc.AHRS;

public class Drivetrain extends SubsystemBase {

  private SwerveModule m_frontLeft;
  private SwerveModule m_frontRight;
  private SwerveModule m_backLeft;
  private SwerveModule m_backRight;

  private SwerveDriveKinematics m_kinematics = new SwerveDriveKinematics(
    new Translation2d(kModuleXOffsetMeters, -kModuleYOffsetMeters),
    new Translation2d(kModuleXOffsetMeters, kModuleYOffsetMeters),
    new Translation2d(-kModuleXOffsetMeters, -kModuleYOffsetMeters),
    new Translation2d(-kModuleXOffsetMeters, kModuleYOffsetMeters));
  
  private SwerveDrivePoseEstimator m_odometer;
  private AHRS m_navX = new AHRS();

  private PIDController m_xController;
  private PIDController m_yController;
  private PIDController m_thetaController;

  public double desiredVelocityAverage = 0;
  public double actualVelocityAverage = 0;

  /** Creates a new Drivetrain. */
  public Drivetrain() {

    m_frontLeft = new SwerveModule(
        kFrontLeftDriveMotorID,
        kFrontLeftSteerMotorID,
        kFrontLeftEncoderID,
        kFrontLeftEncoderOffset);

    m_frontRight = new SwerveModule(
        kFrontRightDriveMotorID,
        kFrontRightSteerMotorID,
        kFrontRightEncoderID,
        kFrontRightEncoderOffset);

    m_backLeft = new SwerveModule(
        kBackLeftDriveMotorID,
        kBackLeftSteerMotorID,
        kBackLeftEncoderID,
        kBackLeftEncoderOffset);

    m_backRight = new SwerveModule(
        kBackRightDriveMotorID,
        kBackRightSteerMotorID,
        kBackRightEncoderID,
        kBackRightEncoderOffset);

    m_odometer = new SwerveDrivePoseEstimator(m_kinematics, getGyroRotation2d(), getModulePositions(), new Pose2d());

  }

  @Override
  public void periodic() { // This method will be called once per scheduler run
    updateOdometry();
  }

  public double getNavxYaw() { // returns the current yaw of the robot
    var pos = m_navX.getYaw() + Timer.getFPGATimestamp() * 0.009 % 360;
    return pos < -180 ? pos + 360 : pos;
  }

  public double getNavxPitch() { // returns the current pitch of the robot
    return m_navX.getPitch();
  }

  public double getNavxRoll() { // returns the current roll of the robot
    return m_navX.getRoll();
  }

  public void zeroGyro() { // zeros the gyro
    m_navX.reset();
  }

  public Rotation2d getGyroRotation2d() { // returns the current rotation of the robot
    return Rotation2d.fromDegrees(-m_navX.getFusedHeading());
  }

  public void updateOdometry() { // updates the odometry of the robot
    m_odometer.update(
        getGyroRotation2d(),
        getModulePositions());
  }

  public double getOdometryYaw() { // returns the current yaw of the robot
    var pos = -m_odometer.getEstimatedPosition().getRotation().getDegrees() % 360;
    return pos < -180 ? pos + 360 : pos;
  }

  public void zeroOdometry() { // zeros the current position of the robot
    m_odometer.resetPosition(getGyroRotation2d(), getModulePositions(), new Pose2d(0, 0, Rotation2d.fromDegrees(0)));
  }

  public void setOdometry(Pose2d pose) { // sets the current position of the robot
    m_odometer.resetPosition(new Rotation2d(-getNavxYaw() * Math.PI / 180), getModulePositions(), pose);
  }
  
  public void setAprilTagOdometry(Pose2d pose) { // sets the current position of the robot
    m_odometer.resetPosition(new Rotation2d(), getModulePositions(), pose);
  }
  
  public SwerveModulePosition[] getModulePositions() { // returns the positions of the modules
    return new SwerveModulePosition[] {
        m_frontLeft.getPosition(),
        m_frontRight.getPosition(),
        m_backLeft.getPosition(),
        m_backRight.getPosition()
    };
  }

  public void setFieldPosition(Pose2d pose) { // sets the current position of the robot
    m_odometer.resetPosition(getGyroRotation2d(), getModulePositions(), pose);
  }

  public Pose2d getFieldPosition() { // returns the current position of the robot
    return m_odometer.getEstimatedPosition();
  }

  public void stop() { // stops the robot (module rotation and translation)
    m_frontLeft.stop();
    m_frontRight.stop();
    m_backLeft.stop();
    m_backRight.stop();
  }

  public void drive(double xSpeed, double ySpeed, double rot) { // drives the robot (always field relative)
    SwerveModuleState[] swerveModuleStates = m_kinematics.toSwerveModuleStates(
        ChassisSpeeds.fromFieldRelativeSpeeds(
            xSpeed,
            ySpeed,
            rot,
            m_odometer.getEstimatedPosition().getRotation()));

    setModuleStates(swerveModuleStates);
  }

  public void drive(double xSpeed, double ySpeed, double rot, boolean fieldRelative) { // drives the robot (field relative or robot relative)
    SwerveModuleState[] swerveModuleStates = m_kinematics.toSwerveModuleStates(
        fieldRelative
            ? ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rot, m_odometer.getEstimatedPosition().getRotation())
            : new ChassisSpeeds(xSpeed, ySpeed, rot));

    setModuleStates(swerveModuleStates);
  }

  public void drive(double xSpeed, double ySpeed, double rot, boolean fieldRelative, Translation2d ctrOfRot) { // drives the robot (field relative or robot relative, variable center of rotation)
    SwerveModuleState[] swerveModuleStates = m_kinematics.toSwerveModuleStates(
        fieldRelative
            ? ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rot, m_odometer.getEstimatedPosition().getRotation())
            : new ChassisSpeeds(xSpeed, ySpeed, rot), ctrOfRot);

    setModuleStates(swerveModuleStates);
  }
  
  public void setModuleStates(SwerveModuleState[] states) { // sets the states of the modules
    SwerveDriveKinematics.desaturateWheelSpeeds(states, kMaxSpeedMetersPerSecond);

    desiredVelocityAverage = Math.abs(states[0].speedMetersPerSecond + states[1].speedMetersPerSecond + states[2].speedMetersPerSecond + states[3].speedMetersPerSecond) / 4;

    m_frontLeft.setDesiredStateClosed(states[0]);
    m_frontRight.setDesiredStateClosed(states[1]);
    m_backLeft.setDesiredStateClosed(states[2]);
    m_backRight.setDesiredStateClosed(states[3]);
  }

  public void DEBUG_OutputAbsoluteEncoderReadings() {
    System.out.println("FL" + m_frontLeft.getAbsoluteAngle());
    System.out.println("FR" + m_frontRight.getAbsoluteAngle());
    System.out.println("BL" + m_backLeft.getAbsoluteAngle());
    System.out.println("BR" + m_backRight.getAbsoluteAngle());
  }
}
