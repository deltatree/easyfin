package de.deltatree.pub.apis.easyfin;

import java.util.List;

import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;

import lombok.Data;

@Data
public class TurnoversResult implements HBCICommandResult {
	private final List<UmsLine> turnovers;
}
