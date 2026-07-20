package de.deltatree.pub.apis.easyfin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.kapott.hbci.structures.Konto;

@Data
@AllArgsConstructor
public class AccountsResult implements HBCICommandResult {

	private List<Konto> accounts;

}
