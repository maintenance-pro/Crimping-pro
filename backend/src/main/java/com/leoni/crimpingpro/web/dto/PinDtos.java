package com.leoni.crimpingpro.web.dto;

import com.leoni.crimpingpro.pin.PinService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Connexion par PIN et administration des postes. Aucun secret dans toString(). */
public final class PinDtos {

  private PinDtos() {}

  public record PinLoginRequest(
      @NotBlank @Pattern(regexp = PinService.DEVICE_ID_REGEX) String deviceId,
      @NotBlank @Size(max = 128) String deviceSecret,
      @NotBlank @Pattern(regexp = PinService.MATRICULE_REGEX) String matricule,
      @NotBlank @Pattern(regexp = "^[0-9]{4,12}$") String pin) {
    @Override
    public String toString() {
      return "PinLoginRequest[deviceId=" + deviceId + ", matricule=" + matricule + ", secret=***, pin=***]";
    }
  }

  public record PinLoginResponse(String customToken) {
    @Override
    public String toString() {
      return "PinLoginResponse[customToken=***]";
    }
  }

  public record DeviceRequest(@NotBlank @Size(max = 60) String label) {}

  public record EnrolledDeviceResponse(String deviceId, String deviceSecret) {
    @Override
    public String toString() {
      return "EnrolledDeviceResponse[deviceId=" + deviceId + ", deviceSecret=***]";
    }
  }

  public record PinRequest(@NotBlank @Pattern(regexp = "^[0-9]{6,8}$") String pin) {
    @Override
    public String toString() {
      return "PinRequest[pin=***]";
    }
  }
}
