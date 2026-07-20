package de.deltatree.pub.apis.easyfin;

import java.util.List;
import lombok.Data;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;

@Data
public class TurnoversResult implements HBCICommandResult {
	private final List<UmsLine> turnovers;
}
