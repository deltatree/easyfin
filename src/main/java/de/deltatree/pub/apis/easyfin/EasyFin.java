package de.deltatree.pub.apis.easyfin;

import java.util.List;
import java.util.stream.Stream;

import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;

public interface EasyFin {

	Stream<UmsLine> getTurnoversAsStream(Konto account);

	List<Konto> getAccounts();
}
