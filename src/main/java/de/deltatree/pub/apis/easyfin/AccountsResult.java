package de.deltatree.pub.apis.easyfin;

import java.util.List;

import org.kapott.hbci.structures.Konto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AccountsResult implements HBCICommandResult {

	private List<Konto> accounts;

}
