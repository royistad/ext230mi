/***************************************************************************************************
 *
 * Business Engine Extension
 *
 ***************************************************************************************************
 * Extension Name: EXT230MI.UpdDocPrinted
 * Type: Transaction
 * Description: This extension is being used to change value on MO header (MWOHED), field
 *              "Order documents printed" (VHWODP).
 *
 * Date         Changed By              Version             Description
 * 20221005     Frank Herman Wik        1.0                 Initial Release
 *
 **************************************************************************************************/
public class UpdDocPrinted extends ExtendM3Transaction {
  private final MIAPI mi
  private final DatabaseAPI database
  private final LoggerAPI logger
  private final ProgramAPI program
  private final MICallerAPI miCaller
  private final UtilityAPI utility

  private String facility

  public UpdDocPrinted( final MIAPI mi,
                        final DatabaseAPI database,
                        final LoggerAPI logger,
                        final ProgramAPI program,
                        final MICallerAPI miCaller,
                        final UtilityAPI util) {
    this.mi = mi
    this.database = database
    this.logger = logger
    this.program = program
    this.miCaller = miCaller
    this.utility = util
  }

  public void main() {
    // Hashmap used to store API input other parameters.
    Map<String, String> parameters = new HashMap<String, String>()

    // Retrieve api input parameters, and do initial checks on data
    if (!retrieveInput(parameters)) {
      return
    }

    // Retrieve facility
    facility = getFacility(parameters)

    // Update MWOHED record
    updateOrderDocumentsPrinted(parameters, facility)
  }

  /**
   * Get facility from warehouse
   * <p>
   * This method getFacility checks if warehouse exists in MITWHL,
   * using API MMS005MI.GetWarehouse. If found, facility is returned.
   *
   * @param  parameters  A hashmap containing all input parameters for EXT230MI.UpdDocPrinted
   * @return  result    Facility
   */
  public String getFacility(Map<String, String> parameters) {
    String result

    Closure<?> output = {value ->
      if(value["errorMessage"] != null && value["errorMessage"].toString() != "" ){
        mi.error(value["errorMessage"].toString())
      } else {
        result = value["FACI"].toString()
      }
    }

    Map<String,String> input = new HashMap<String, String>()
    input.put("WHLO", parameters.get("WHLO"))
    this.miCaller.setListMaxRecords(1)
    logger.debug("MMS005MI, input: " + input)
    this.miCaller.call("MMS005MI", "GetWarehouse", input, output)

    logger.debug("MMS005MI result: " + result)
    return result
  }

  /**
   * Performs an update on M3 table MWOHED
   * <p>
   * This method update M3 table MWOHED.
   * If the update record was unsuccesful, an MI error message will be shown to the user
   *
   * @param  parameters  A hashmap containing all input parameters for EXT230MI.UpdDocPrinted
   * @param  facility    Facility information from warehouse
   * @return void
   */
  private void updateOrderDocumentsPrinted(Map<String, String> parameters, String facility) {
    DBAction query = database
      .table("MWOHED")
      .index("00")
      .selection("VHCONO", "VHFACI", "VHPRNO", "VHMFNO", "VHWODP", "VHLMDT", "VHCHNO", "VHCHID")
      .build()

    DBContainer container = query.getContainer()
    container.set("VHCONO", Integer.parseInt(parameters.get("CONO")))
    container.set("VHFACI", facility)
    container.set("VHPRNO", parameters.get("PRNO"))
    container.set("VHMFNO", parameters.get("MFNO"))

    Closure<?> updateCallBack = { LockedResult lockedResult ->
      lockedResult.set("VHWODP", Integer.parseInt(parameters.get("WODP")))
      lockedResult.set("VHLMDT", utility.call("DateUtil","currentDateY8AsInt"))
      lockedResult.set("VHCHNO", lockedResult.getInt("VHCHNO") + 1)
      lockedResult.set("VHCHID", program.getUser())
      lockedResult.update()
    }

    if (!query.readLock(container, updateCallBack)) {
      mi.error("Could not update record in table MWOHED")
    }
  }

/**
 * Retrieves and validates API input parameters. Return true if all input is valid, and false if it encounters invalid input.
 * <p>
 * This method retrieves the API input parameters, and validates them. Checks are made to see
 * if mandatory fields have been filled in, and if fields are in the correct format. All input
 * parameters are stored in the parameters hashmap passed as input to this method. This method
 * returns true if all input fields are valid, and false if an invalid field was encountered.
 *
 * @param    parameters  A hashmap in which to store all API input parameters
 * @return   boolean     true if all input API input fields are valid, false if not
 */
  private boolean retrieveInput(Map<String, String> parameters) {
    String cono // Company
    String whlo // Warehouse (mandatory)
    String prno // Product (mandatory)
    String mfno // Manufacturing order number (mandatory)
    String wodp // Order documents printed (mandatory)

    // Company
    if (mi.inData.get("CONO") == null || mi.inData.get("CONO").trim() == "") {
      cono = String.valueOf((Integer)program.getLDAZD().CONO)
    } else {
      try {
        cono = String.valueOf(Integer.parseInt(mi.inData.get("CONO").trim()))
      } catch (NumberFormatException e) {
        mi.error("Company must be numerical", "CONO", "01")
        return false
      }
    }

    // Facility
    whlo = mi.inData.get("WHLO") == null ? "" : mi.inData.get("WHLO").trim()
    if (whlo == "") {
      mi.error("Warehouse must be entered", "WHLO", "01")
      return false
    }

    // Product
    prno = mi.inData.get("PRNO") == null ? "" : mi.inData.get("PRNO").trim()
    if (prno == "") {
      mi.error("Product must be entered", "PRNO", "01")
      return false
    }

    // Product
    mfno = mi.inData.get("MFNO") == null ? "" : mi.inData.get("MFNO").trim()
    if (mfno == "") {
      mi.error("Product must be entered", "MFNO", "01")
      return false
    }

    // Order documents printed
    wodp = mi.inData.get("WODP") == null ? "0" : mi.inData.get("WODP").trim()
    if (Integer.parseInt(wodp) != 0 && Integer.parseInt(wodp) != 1) {
      mi.error("Order documents printed ${wodp} must be 0 or 1", "WODP", "01")
      return false
    }

    // Store input parameters in <parameters> hashmap
    parameters.put("CONO", cono)
    parameters.put("WHLO", whlo)
    parameters.put("PRNO", prno)
    parameters.put("MFNO", mfno)
    parameters.put("WODP", wodp)

    logger.debug("Input parameters: " + parameters)

    return true
  }

}
