const form = document.getElementById('credit-form');
const statusElement = document.getElementById('status');
let creditInputApplyUrl = '/api/credit/apply';

const toTitleCase = value =>
  value
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');

const digitsOnly = value => value.replace(/\D/g, '');
const uppercaseCompact = value => value.replace(/\s+/g, '').toUpperCase();
const normalizeExpiry = value => value.trim().replace(/-/g, '/');

const configReady = (async () => {
  try {
    const response = await fetch('/ui-config');
    if (!response.ok) {
      return;
    }

    const config = await response.json();
    if (config.creditInputApplyUrl && typeof config.creditInputApplyUrl === 'string') {
      creditInputApplyUrl = config.creditInputApplyUrl;
    }
  } catch (error) {
    // Fall back to the Credit UI endpoint, which proxies through Envoy.
  }
})();

form.addEventListener('submit', async event => {
  event.preventDefault();
  await configReady;
  statusElement.style.display = 'none';
  statusElement.className = 'status';
  statusElement.textContent = 'Submitting application...';
  statusElement.style.display = 'block';

  const normalizedName = toTitleCase(document.getElementById('name').value);
  const normalizedPhone = digitsOnly(document.getElementById('phone').value);
  const normalizedAadhar = digitsOnly(document.getElementById('aadhar').value);
  const normalizedPan = uppercaseCompact(document.getElementById('pan').value);
  const normalizedCreditCard = digitsOnly(document.getElementById('creditCard').value);
  const normalizedExpiry = normalizeExpiry(document.getElementById('expiry').value);
  const normalizedCvc = digitsOnly(document.getElementById('cvc').value);

  document.getElementById('name').value = normalizedName;
  document.getElementById('phone').value = normalizedPhone;
  document.getElementById('aadhar').value = normalizedAadhar;
  document.getElementById('pan').value = normalizedPan;
  document.getElementById('creditCard').value = normalizedCreditCard;
  document.getElementById('expiry').value = normalizedExpiry;
  document.getElementById('cvc').value = normalizedCvc;

  if (!form.reportValidity()) {
    statusElement.className = 'status error';
    statusElement.textContent = 'Please correct the form fields and submit again.';
    return;
  }

  const payload = {
    name: normalizedName,
    phoneNumber: normalizedPhone,
    aadharNumber: normalizedAadhar,
    panNumber: normalizedPan,
    creditCardNumber: normalizedCreditCard,
    creditCardExpiry: normalizedExpiry,
    cvc: normalizedCvc
  };

  try {
    const response = await fetch(creditInputApplyUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    let result = {};
    const responseContentType = response.headers.get('content-type') || '';
    if (responseContentType.includes('application/json')) {
      result = await response.json();
    } else {
      const responseText = await response.text();
      if (responseText && responseText.trim()) {
        result = { message: responseText };
      }
    }

    if (!response.ok) {
      statusElement.className = 'status error';
      statusElement.textContent = result.message || `Unable to submit application (HTTP ${response.status}).`;
      return;
    }

    statusElement.className = 'status';
    statusElement.textContent = `Application submitted successfully. Your application number is ${result.applicationNumber}.`;
    form.reset();
  } catch (error) {
    statusElement.className = 'status error';
    statusElement.textContent = `Failed to contact the credit-input service. ${error.message}`;
  }
});
