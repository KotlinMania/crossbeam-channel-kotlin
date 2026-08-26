#if canImport(Testing)
import Testing
import CrossbeamChannel

@Suite("CrossbeamChannel Swift Export Tests")
struct CrossbeamChannelExportTests {
    @Test("Swift module loads")
    func testSwiftModuleLoads() {
        #expect(Bool(true), "CrossbeamChannel swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import CrossbeamChannel

final class CrossbeamChannelExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "CrossbeamChannel swift module imported cleanly")
    }
}
#endif
